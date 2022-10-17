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
package com.github.lindenb.jvarkit.tools.ibd;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.github.lindenb.jvarkit.io.IOUtils;
import com.github.lindenb.jvarkit.iterator.EqualIterator;
import com.github.lindenb.jvarkit.lang.JvarkitException;
import com.github.lindenb.jvarkit.util.jcommander.Launcher;
import com.github.lindenb.jvarkit.util.jcommander.Program;
import com.github.lindenb.jvarkit.util.log.Logger;
import com.github.lindenb.jvarkit.util.picard.AbstractDataCodec;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalTreeMap;
import htsjdk.samtools.util.Locatable;
import htsjdk.samtools.util.SortingCollection;

@Program(
		name="ibdreformater01",
		keywords={"ibd"},
		description="reformater ibd data for Fabien Laporte",
		generate_doc = false,
		creationDate = "20221014",
		modificationDate = "20221014"
		)

public class IBDReformater01 extends Launcher{
	private static final Logger LOG = Logger.build(IBDReformater01.class).make();

	@Parameter(names={"-o","--output"},description=OPT_OUPUT_FILE_OR_STDOUT)
	private Path outFile=null;
	@Parameter(names={"--bim"},description="Bim File",required = true)
	private Path bimFile =null;
	@Parameter(names={"--ibd"},description="IBD File",required = true)
	private Path ibdFile =null;

	@ParametersDelegate
	private WritingSortingCollection writingSortingCollection = new WritingSortingCollection();

	
public static class BimRecord implements Locatable, Comparable<BimRecord> {
	final String contig;
	final int pos;
	boolean keep =false;
	BimRecord(final String contig,int pos) {
		this.contig = contig;
		this.pos = pos;
		}
	@Override
	public String getContig() {
		return contig;
		}
	@Override
	public int getStart() {
		return pos;
		}
	@Override
	public int getEnd() {
		return pos;
		}
	@Override
	public int compareTo(BimRecord o) {
		int i = contig.compareTo(o.contig);
		if(i!=0) return i;
		return Integer.compare(pos, o.pos);
		}
	@Override
	public String toString() {
		return contig+":"+pos;
		}
	}

private static class IBDRecord implements Locatable{
	String sn1;
	int haplo1;
	String sn2;
	int haplo2;
	
	String contig;
	int start;
	int end;
	@Override
	public String getContig() {
		return contig;
		}
	@Override
	public int getStart() {
		return start;
		}
	@Override
	public int getEnd() {
		return end;
		}
	public int compareSample(final IBDRecord o) {
		int i = sn1.compareTo(o.sn1);
		if(i!=0) return i;
		return sn2.compareTo(o.sn2);
		}
	public int compareSamplePos(final IBDRecord o) {
		int i = compareSample(o);
		if(i!=0) return i;
		i = contig.compareTo(o.contig);
		if(i!=0) return i;
		i = Integer.compare(start, o.start);
		if(i!=0) return i;
		return Integer.compare(end, o.end);
		}
	int getDigestedHaplo() {
		if(haplo1==haplo2) return haplo1;
		return -1;//TODO
		}
	@Override
	public String toString()
		{
		return sn1+"\t"+haplo1+"\t"+sn2+"\t"+haplo2+"\t"+contig+":"+start+"-"+end;
		}
	}

private static class IBDRecordCodec extends AbstractDataCodec<IBDRecord> {
	@Override
	public IBDRecord decode(DataInputStream dis) throws IOException {
		final IBDRecord rec = new IBDRecord();
		try {
			rec.sn1 = dis.readUTF();
			}
		catch(final EOFException err) {
			return null;
			}
		rec.haplo1 = dis.readInt();
		rec.sn2 = dis.readUTF();
		rec.haplo2 = dis.readInt();
		rec.contig = dis.readUTF();
		rec.start = dis.readInt();
		rec.end = dis.readInt();
		return rec;
		}
	@Override
	public void encode(final DataOutputStream dos, final IBDRecord rec) throws IOException {
		dos.writeUTF(rec.sn1);
		dos.writeInt(rec.haplo1);
		dos.writeUTF(rec.sn2);
		dos.writeInt(rec.haplo2);
		dos.writeUTF(rec.contig);
		dos.writeInt(rec.start);
		dos.writeInt(rec.end);
		}
	@Override
	public IBDRecordCodec clone() {
		return new IBDRecordCodec();
		}
	}



@Override
public int doWork(List<String> args) {
	try {
		if(!args.isEmpty()) {
			LOG.error("illegal numbers of arguments");
			return -1;
			}
		final IntervalTreeMap<BimRecord> bimRecordsIntervalTreeMap = new IntervalTreeMap<>();

		final List<BimRecord> bims = new ArrayList<>(100_000);
		final Pattern ws = Pattern.compile("[\\s]+");
		try(BufferedReader br = IOUtils.openPathForBufferedReading(this.bimFile)) {
			String line;
			while((line=br.readLine())!=null) {
				final String[] tokens = ws.split(line);
				if(tokens.length<4) throw new JvarkitException.TokenErrors(4, tokens);

				final BimRecord rec = new BimRecord(tokens[0], Integer.parseInt(tokens[3]));
				bimRecordsIntervalTreeMap.put(new Interval(rec), rec);
				bims.add(rec);
				}
			}
		Collections.sort(bims);
		LOG.info("number of record in "+this.bimFile+" "+bims.size());
		
		SortingCollection<IBDRecord> sorter = 
				SortingCollection.newInstance(
						IBDRecord.class,
						new IBDRecordCodec(),
						(A,B)->A.compareSamplePos(B),
						writingSortingCollection.getMaxRecordsInRam(),
						writingSortingCollection.getTmpPaths()
						);
		sorter.setDestructiveIteration(true);
		try(BufferedReader br = IOUtils.openPathForBufferedReading(this.ibdFile)) {
			String line;
			while((line=br.readLine())!=null) {
				final String[] tokens = ws.split(line);
				if(tokens.length<7) throw new JvarkitException.TokenErrors(7, tokens);
				final IBDRecord rec = new IBDRecord();
				if(tokens[0].compareTo(tokens[2]) <= 0) {
					rec.sn1 = tokens[0];
					rec.haplo1 = Integer.parseInt(tokens[1]);
					rec.sn2 = tokens[2];
					rec.haplo2 = Integer.parseInt(tokens[3]);
					}
				else
					{
					rec.sn2 = tokens[0];
					rec.haplo2 = Integer.parseInt(tokens[1]);
					rec.sn1 = tokens[2];
					rec.haplo1 = Integer.parseInt(tokens[3]);
					}
				rec.contig = tokens[4];
				
				rec.start = Integer.parseInt(tokens[5]);
				rec.end = Integer.parseInt(tokens[6]);
				boolean found=false;
				for(BimRecord bim:bimRecordsIntervalTreeMap.getOverlapping(rec)) {
					bim.keep=true;
					found=true;
					}
				if(found) {
					sorter.add(rec);
					}
				}
			
			}
		sorter.doneAdding();
		bims.removeIf(B->!B.keep);
		LOG.error("Removing markers without overlap. Now N="+bims.size());
		
		if(bims.isEmpty()) {
			LOG.error("no overlapping record from BIM was found");
			return -1;
			}
		try(PrintWriter pw =super.openPathOrStdoutAsPrintWriter(this.outFile)) {
			pw.append("Couple");
			for(BimRecord rec:bims) {
				pw.append("\t");
				pw.append(rec.toString());
				}
			pw.append("\n");
			try(CloseableIterator<IBDRecord> iter0 = sorter.iterator()) {
				final EqualIterator<IBDRecord> iter = new EqualIterator<>(iter0, (A,B)->A.compareSample(B));
				while(iter.hasNext()) {
					final List<IBDRecord> row = iter.next();
					final IBDRecord first = row.get(0);
					pw.print(first.sn1);
					pw.print("/");
					pw.print(first.sn2);
				
					final IntervalTreeMap<IBDRecord> treemap = new IntervalTreeMap<>();
					for(IBDRecord ibd:row) {
						final Interval r = new Interval(ibd);
						if(treemap.containsKey(r)) {
							LOG.error("duplicate range:\n"+ibd+"\nvs\n"+treemap.get(r));
							iter.close();
							return -1;
							}
						treemap.put(r, ibd);
						}
					
					for(BimRecord rec:bims) {
						pw.append("\t");
						final Collection<Integer> hapset = treemap.
								getOverlapping(rec).
								stream().
								map(T->T.getDigestedHaplo()).
								collect(Collectors.toSet());
						if(hapset.isEmpty())
							{
							pw.append("0");
							}
						else if(hapset.size()==1)
							{
							pw.append(String.valueOf(hapset.iterator().next()));
							}
						else
							{
							iter.close();
							throw new IllegalStateException("ambigous: "+ rec+" : " + treemap.
									getOverlapping(rec).stream().map(T->T.toString()).collect(Collectors.joining(" | ")));
							}
						}
					pw.println();
					}
				iter.close();
				}
			pw.flush();
			}
		sorter.cleanup();
		return 0;
		}
	catch(final Throwable err) {
		LOG.error(err);
		return -1;
		}
	}

public static void main(String[] args) {
	new IBDReformater01().instanceMainWithExit(args);
	}
}
