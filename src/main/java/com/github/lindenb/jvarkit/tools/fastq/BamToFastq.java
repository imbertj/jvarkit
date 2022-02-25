/*
The MIT License (MIT)

Copyright (c) 2022 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


*/
package com.github.lindenb.jvarkit.tools.fastq;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import htsjdk.samtools.fastq.BasicFastqWriter;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.BAMRecordCodec;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.PeekableIterator;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.SortingCollection;
import htsjdk.samtools.util.StringUtil;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.github.lindenb.jvarkit.fastq.FastqPairedWriter;
import com.github.lindenb.jvarkit.fastq.FastqPairedWriterFactory;
import com.github.lindenb.jvarkit.fastq.FastqUtils;
import com.github.lindenb.jvarkit.iterator.EqualIterator;
import com.github.lindenb.jvarkit.jcommander.MultiBamLauncher;
import com.github.lindenb.jvarkit.lang.StringUtils;
import com.github.lindenb.jvarkit.util.bio.DistanceParser;
import com.github.lindenb.jvarkit.util.jcommander.NoSplitter;
import com.github.lindenb.jvarkit.util.jcommander.Program;
import com.github.lindenb.jvarkit.util.log.Logger;


/**

BEGIN_DOC

## Example

```
$ java -jar dist/bam2fastq.jar  src/test/resources/S2.bam  | head -n 16
@RF01_38_466_2:0:0_3:0:0_8f
TCTAGTCAGAATATTTATCATTTATATATAACTCACAATCCGCATTTCAAATTCCAATATACTATTCTTC
+
2222222222222222222222222222222222222222222222222222222222222222222222
@RF01_38_466_2:0:0_3:0:0_8f
CCAACCAGAACATAACTGCATTTAAATTTGATGATAATTAAGTTAAACTTGCTGGATCCATCAATTAATC
+
2222222222222222222222222222222222222222222222222222222222222222222222
@RF01_20_472_1:0:0_3:0:0_32
GTTTTACCCACCAGAACATAACTGCATTTAAATTTGATGATAATGAAGTTAAAATTGCTGGCTCCATCAA
+
2222222222222222222222222222222222222222222222222222222222222222222222
@RF01_20_472_1:0:0_3:0:0_32
TGGGGAAGTATAATCTAATCTTGTCAGAATATTTATCATTTATATATAACTCACAATCCGCAGTTCAACT
+
2222222222222222222222222222222222222222222222222222222222222222222222
```


## Cited In:

  * "Plastomes of nine hornbeams and phylogenetic implications", Ying Li & al;  Ecology and Evolution, 2018; DOI: 10.1002/ece3.4414; https://onlinelibrary.wiley.com/doi/pdf/10.1002/ece3.4414 

END_DOC
*/
@Program(name="bam2fastq",
	description="convert paired-end SAM to fastq using a memory buffer.",
	keywords={"fastq","bam"},
	modificationDate="20220225",
	creationDate="20131120"
	)
public class BamToFastq extends MultiBamLauncher
	{
	private static final Logger LOG = Logger.build(BamToFastq.class).make();

	private static class NullFastqWriter implements FastqWriter {
		@Override
		public void write(FastqRecord rec) {}
		@Override
		public void close() {}
		}
	
	

	@Parameter(names={"-R0","--single"},description="Save single-end to this file. If unspecified, single-end reads are ignored.")
	private File singleFastq = null;

	@Parameter(names={"-R1","--forward"},description="Save fastq_R1 to file (default: stdout)")
	private File forwardFile = null;

	@Parameter(names={"-R2","--reverse"},description="Save fastq_R2 to file (default: interlaced with forward)")
	private File reverseFile = null;

	@Parameter(names={"-U","--unpaired"},description="Save unresolved pair to file. If unspecified, unresolved reads are ignored.")
	private File unpairedFile = null;
	
	@ParametersDelegate
	private WritingSortingCollection sortingCollection = new WritingSortingCollection();
	
	@Parameter(names={"-d","--distance"},description="put the reads in memory if they're lying within that distance. " + DistanceParser.OPT_DESCRIPTION,splitter=NoSplitter.class,converter=DistanceParser.StringConverter.class)
	private int distance = 5_000;
	
	
	@Override
	protected Logger getLogger()
		{
		return LOG;
		}
	

	/** convert SAMRecord to fastq */
	private FastqRecord toFastq(final SAMRecord rec) {
		String readString = SAMRecord.NULL_SEQUENCE==rec.getReadBases()?"":rec.getReadString();
		String baseQualities = SAMRecord.NULL_QUALS==rec.getBaseQualities()?
				StringUtils.repeat(readString.length(), '#'):
				rec.getBaseQualityString();

		
		if(rec.getReadNegativeStrandFlag()) {
			readString = SequenceUtil.reverseComplement(readString);
			baseQualities = StringUtil.reverseString(baseQualities);
			}
		return new FastqRecord(
				rec.getReadName(),
				readString,
				"",
				baseQualities
				);
		}
	
	@Override
	protected int processInput(final SAMFileHeader header, final CloseableIterator<SAMRecord> iter0)
		{
		final Comparator<SAMRecord> queryNameComparator= (A,B)->A.getReadName().compareTo(B.getReadName());
		SortingCollection<SAMRecord> sortingSAMRecord=null;
		final ArrayList<SAMRecord> buffer = new ArrayList<>(50_000);
		FastqWriter singleEndWriter=null;
		FastqWriter unpairedWriter=null;
		FastqPairedWriter R1R2writer=null;
		final PeekableIterator<SAMRecord> iter = new PeekableIterator<>(iter0);
		try {
			if(!SAMFileHeader.SortOrder.coordinate.equals(header.getSortOrder())) {
				LOG.error("Input is not sorted on coordinate. got : " + header.getSortOrder());
				return -1;
				}

			if(singleFastq!=null) FastqUtils.validateFastqFilename(singleFastq);
			if(unpairedFile!=null) FastqUtils.validateFastqFilename(unpairedFile);
			singleEndWriter = this.singleFastq==null?
						new NullFastqWriter():
						new BasicFastqWriter(this.singleFastq);
			unpairedWriter = this.unpairedFile==null?
						new NullFastqWriter():
						new BasicFastqWriter(this.unpairedFile);
			if(this.forwardFile==null && this.reverseFile==null) {
				R1R2writer = new FastqPairedWriterFactory().open(stdout());
				}
			else if(this.forwardFile!=null && this.reverseFile==null) {
				R1R2writer = new FastqPairedWriterFactory().open(this.forwardFile);
			} else if(this.forwardFile!=null && this.reverseFile!=null) {
				R1R2writer = new FastqPairedWriterFactory().open(this.forwardFile,this.reverseFile);
			}
			else {
				LOG.error("R1 undefined and R2 is defined");
				return -1;
			}
		
			sortingSAMRecord = SortingCollection.newInstance(
					SAMRecord.class,
					new BAMRecordCodec(header),
					queryNameComparator,
					sortingCollection.getMaxRecordsInRam(),
					sortingCollection.getTmpPaths()
					);
			sortingSAMRecord.setDestructiveIteration(true);
			while(iter.hasNext()) {
				final SAMRecord rec= iter.next();
				if(rec.isSecondaryOrSupplementary()) continue;
				if(!rec.getReadPairedFlag()) {
					singleEndWriter.write(toFastq(rec));
					continue;
					}

				if((rec.getReadUnmappedFlag() || rec.getMateUnmappedFlag()) && iter.hasNext()) {
					final SAMRecord rec2= iter.peek();
					if(!rec2.isSecondaryOrSupplementary() &&
						queryNameComparator.compare(rec, rec2)==0) {
						 if(rec2.getFirstOfPairFlag() && rec.getSecondOfPairFlag()) {
						 	//consumme
						 	iter.next();
						 	R1R2writer.write(toFastq(rec2), toFastq(rec));
						 	continue;
							}
						else if(rec.getFirstOfPairFlag() && rec2.getSecondOfPairFlag()) {
							//consumme
						 	iter.next();
							R1R2writer.write(toFastq(rec), toFastq(rec2));
							continue;
							}
						}
					}
				
				
				
				if(rec.getReadUnmappedFlag() ||
					rec.getMateUnmappedFlag() ||
					!rec.getReferenceName().equals(rec.getMateReferenceName()) ||
					Math.abs(rec.getInferredInsertSize()) > this.distance) {
					sortingSAMRecord.add(rec);
					continue;
					}
				
				while(!buffer.isEmpty() && !buffer.get(0).getReferenceName().equals(rec.getReferenceName())) {
					sortingSAMRecord.add(buffer.remove(0));
					}
				
				while(!buffer.isEmpty() && (rec.getAlignmentStart() - buffer.get(0).getAlignmentStart()) > this.distance) {
					sortingSAMRecord.add(buffer.remove(0));
					}

				
				if(rec.getAlignmentStart() < rec.getMateAlignmentStart()) {
					buffer.add(rec);
					continue;
					}

				SAMRecord mate = null;
				int i=0;
				while(i< buffer.size()) {
					final SAMRecord rec2 = buffer.get(i);
					if(queryNameComparator.compare(rec2, rec)==0) {
						mate = rec2;
						buffer.remove(i);
						break;
						}
					if(rec2.getAlignmentStart() > rec.getMateAlignmentStart()) {
						break;
						}
					++i;
					}
				if(mate==null) {
					unpairedWriter.write(toFastq(rec));
					}
				else if(mate.getFirstOfPairFlag() && rec.getSecondOfPairFlag()) {
					R1R2writer.write(toFastq(mate), toFastq(rec));
					}
				else if(rec.getFirstOfPairFlag() && mate.getSecondOfPairFlag()) {
					R1R2writer.write(toFastq(rec), toFastq(mate));
					}
				else
					{
					unpairedWriter.write(toFastq(rec));
					unpairedWriter.write(toFastq(mate));
					}
				}
			for(final SAMRecord rec:buffer) {
				sortingSAMRecord.add(rec);
				}
			buffer.clear();

			sortingSAMRecord.doneAdding();
			try(CloseableIterator<SAMRecord> iter2=sortingSAMRecord.iterator()) {
				try(EqualIterator<SAMRecord> eq = new EqualIterator<>(iter2,queryNameComparator)) {
					while(eq.hasNext()) {
						final List<SAMRecord> L = eq.next();
						if(L.size()==2) {
							if(L.get(0).getFirstOfPairFlag() && L.get(1).getSecondOfPairFlag()) {
								R1R2writer.write(toFastq(L.get(0)), toFastq(L.get(1)));
								}
							else if(L.get(1).getFirstOfPairFlag() && L.get(0).getSecondOfPairFlag()) {
								R1R2writer.write(toFastq(L.get(1)), toFastq(L.get(0)));
								}
							else
								{
								unpairedWriter.write(toFastq(L.get(0)));
								unpairedWriter.write(toFastq(L.get(1)));
								}
							}
						else
							{
							for(SAMRecord rec2:L) {
								unpairedWriter.write(toFastq(rec2));
								}
							}
						}
					}
				}
			sortingSAMRecord.cleanup();
			return 0;
			}
		catch(final Throwable err ) {
			LOG.error(err);
			return -1;
			}
		finally {
			iter.close();
			if(R1R2writer!=null) try{R1R2writer.close();} catch(Throwable err) {}
			if(unpairedWriter!=null) try{unpairedWriter.close();} catch(Throwable err) {}
			if(singleEndWriter!=null) try{singleEndWriter.close();} catch(Throwable err) {}
			}
		}
	

	public static void main(final String[] args) {
		new BamToFastq().instanceMainWithExit(args);
		}
	}
