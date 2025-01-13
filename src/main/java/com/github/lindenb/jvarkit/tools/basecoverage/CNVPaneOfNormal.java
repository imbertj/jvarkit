/*
The MIT License (MIT)

Copyright (c) 2025 Pierre Lindenbaum

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
package com.github.lindenb.jvarkit.tools.basecoverage;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import com.beust.jcommander.Parameter;
import com.github.lindenb.jvarkit.canvas.Canvas;
import com.github.lindenb.jvarkit.lang.StringUtils;
import com.github.lindenb.jvarkit.samtools.util.SimpleInterval;
import com.github.lindenb.jvarkit.util.FunctionalMap;
import com.github.lindenb.jvarkit.util.JVarkitVersion;
import com.github.lindenb.jvarkit.util.jcommander.Program;
import com.github.lindenb.jvarkit.util.log.Logger;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CoordMath;
import htsjdk.samtools.util.Locatable;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFIterator;
import htsjdk.variant.vcf.VCFIteratorBuilder;
import htsjdk.variant.vcf.VCFStandardHeaderLines;
/**
BEGIN_DOC

## Input



## Example:

```

```

END_DOC
 */
@Program(name="cnvpanelofnormal",
	description="Call CNV from panel of normal computed with 'basecoverage'",
	keywords={"depth","bam","sam","coverage","vcf","cnv","sv"},
	creationDate="20241123",
	modificationDate="20241123",
	jvarkit_amalgamion =  false,
	menu="CNV/SV"
	)
public class CNVPaneOfNormal extends AbstractBaseCov {
	private static Logger LOG=Logger.build(CNVPaneOfNormal.class).make();
	@Parameter(names={"-R","--reference"},description=INDEXED_FASTA_REFERENCE_DESCRIPTION)
	private Path faidx = null;
	@Parameter(names={"-V","--vcf","--normal"},description="Vcf panel of normal generated by 'basecoverage'",required = true)
	private Path vcf_panel_of_normal = null;
	@Parameter(names={"-m"},description="min CNV size")
	private int min_cnv_size=0;
	@Parameter(names={"-S"},description="merge CNV within distance of 'x'.")
	private int merge_adjacent=0;
	@Parameter(names={"-t","--treshold"},description="CNV treshold DEL if depth < 1.0 - x and DUP  if depth > 1.0 + x")
	private float treshold = 0.4f;
	@Parameter(names={"-f","--iqr"},description="IQR factor to detect outliers")
	private double iqr_factor = 1.5;

	@Parameter(names={"--canvas"},description="Canvas output")
	private Path canvasOutput=null;

	
	private static class CNV {
		int start;
		int end;
		int status;
		double sum = 0.0;
		int count=0;
		int length() { return end-start;}
		@Override
		public String toString() {
			return String.valueOf(start)+"-"+end+" status="+status+" DP="+(sum/(double)count)+" len="+length();
			}
	}
	
	private static class BoxPlot {
		/** first quartile */
		double Q1 = 1.0;
		@SuppressWarnings("unused")
		double median = 1.0;
		/** 3rd quartile */
		double Q3 = 1.0;
		/** min/max */
		double min = 1.0;
		double max = 1.0;
		
		
		/** Interquartile Range */
		private double IQR() { return Q3-Q1;}
		private double upper() { return Q1 - IQR()*1.5;}
		private double lower() { return Q3 + IQR()*1.5;}
	}	
	
	public CNVPaneOfNormal() {
		}
	
	private boolean isDel(final BoxPlot bx,double v) {
		//if(v < (bx.min - (bx.max-bx.min)*0.1)) return true;
		//return false;
		return v <= (1.0 - this.treshold) &&  ( v <= (bx.Q1 - bx.IQR()*iqr_factor)  || v < bx.min);
		}
	
	private boolean isDup(final BoxPlot bx,double v) {
		//if(v > (bx.max + (bx.max-bx.min)*0.1)) return true;
		//return false;
		return v >= (1.0 + this.treshold) &&  ( v >= (bx.Q3 + bx.IQR()*iqr_factor) || v > bx.max);
		}
	
	@Override
	public int doWork(final List<String> args)
		{
		Plot plotter=null;
		try {
			final String input = oneAndOnlyOneFile(args);
			
			
			
			final List<BoxPlot> boxplots = new ArrayList<>();
			String rgn_contig = null;
			int rgn_start=-1;
			int rgn_end=-1;
			try(VCFIterator iter= new  VCFIteratorBuilder().open(this.vcf_panel_of_normal)) {
				final VCFHeader header=iter.getHeader();
				if(!header.hasGenotypingData()) {
					LOG.error("there is no genotype data in "+this.vcf_panel_of_normal);
					}
				final VCFFormatHeaderLine formatMedianDP = header.getFormatHeaderLine(FORMAT_NORM_DEPH);
				if(formatMedianDP==null) throw new IllegalArgumentException("FORMAT ID="+FORMAT_NORM_DEPH+" in vcf header");
				
				
				while(iter.hasNext()) {
					final VariantContext ctx = iter.next();
					if(rgn_contig==null) {
						rgn_contig = ctx.getContig();
						rgn_start = ctx.getStart();
						rgn_end = rgn_start;
						}
					else
						{
						if(!ctx.getContig().equals(rgn_contig)) throw new IllegalArgumentException("multiple chromosomes "+rgn_contig+" vs "+ctx.getContig());
						if(rgn_end+1!=ctx.getStart())  throw new IllegalArgumentException("expected POS="+rgn_end+" but got "+ctx.getStart());
						rgn_end = ctx.getStart();
						}

					
					final BoxPlot bx = new BoxPlot();
					@SuppressWarnings("deprecation")
					final double[] array_d = ctx.getGenotypes().
						stream().
						filter(GT->GT.hasExtendedAttribute(formatMedianDP.getID())).
						mapToDouble(GT->(Double)GT.getAttributeAsDouble(formatMedianDP.getID(),0)).
						sorted().
						toArray();
					
					final Percentile percentile = new Percentile();
					bx.Q1 =  percentile.evaluate(array_d,25);
					bx.median =  percentile.evaluate(array_d,50);
					bx.Q3 =  percentile.evaluate(array_d,75);
					bx.min = Arrays.stream(array_d).min().orElse(1.0);
					bx.max = Arrays.stream(array_d).max().orElse(1.0);
					boxplots.add(bx);
					
					}
				}
			if(boxplots.isEmpty()) throw new IllegalArgumentException("no variant was found in "+this.vcf_panel_of_normal);
			final Locatable queryInterval = new SimpleInterval(rgn_contig,rgn_start,rgn_end);
			
			
			if(this.canvasOutput!=null) {
				plotter= new Plot(this.canvasOutput, queryInterval.getLengthOnReference());
				
				
				final List<Point2D> points = new ArrayList<>(queryInterval.getLengthOnReference());
				for(int i=0;i< boxplots.size();++i) {
					points.add(plotter.toPoint(i, boxplots.get(i).max));
					}
				plotter.simplify(points, L->L.stream().mapToDouble(V->V).max().getAsDouble());
				plotter.canvas.polyline(points,FunctionalMap.of(Canvas.KEY_STROKE,Color.RED,Canvas.KEY_STROKE_WIDTH,0.5));
				
				
				points.clear();
				for(int i=0;i< boxplots.size();++i) {
					points.add(plotter.toPoint(i, boxplots.get(i).min));
					}
				plotter.simplify(points, L->L.stream().mapToDouble(V->V).min().getAsDouble());
				plotter.canvas.polyline(points,FunctionalMap.of(Canvas.KEY_STROKE,Color.RED,Canvas.KEY_STROKE_WIDTH,0.5));
				
				
				
				points.clear();
				for(int i=0;i< boxplots.size();++i) {
					points.add(plotter.toPoint(i, boxplots.get(i).lower()));
					}
				plotter.simplify(points, L->L.stream().mapToDouble(V->V).min().getAsDouble());
				plotter.canvas.polyline(points,FunctionalMap.of(Canvas.KEY_STROKE,Color.CYAN,Canvas.KEY_STROKE_WIDTH,0.5));
				
				points.clear();
				for(int i=0;i< boxplots.size();++i) {
					points.add(plotter.toPoint(i, boxplots.get(i).upper()));
					}
				plotter.simplify(points, L->L.stream().mapToDouble(V->V).max().getAsDouble());
				plotter.canvas.polyline(points,FunctionalMap.of(Canvas.KEY_STROKE,Color.CYAN,Canvas.KEY_STROKE_WIDTH,0.5));
				
				
				points.clear();
				for(int i=0;i< boxplots.size();++i) {
					points.add(plotter.toPoint(i, boxplots.get(i).median));
					}
				plotter.simplify(points, L->L.stream().mapToDouble(V->V).average().getAsDouble());
				plotter.canvas.polyline(points,FunctionalMap.of(Canvas.KEY_STROKE,Color.ORANGE,Canvas.KEY_STROKE_WIDTH,0.5));
				}
			
			final String sampleName;
			final SAMSequenceDictionary dict;
			final double[] coverage_norm;
			final SamReaderFactory srf = super.createSamReaderFactory();
			if(this.faidx!=null) srf.referenceSequence(this.faidx);
			try(SamReader sr =srf.open(SamInputResource.of(input))) {
				if(!sr.hasIndex()) {
					throw new IllegalArgumentException("bam "+input+" is not indexed");
					}
				final SAMFileHeader header = sr.getFileHeader();
				dict = header.getSequenceDictionary();
				sampleName = header.getReadGroups().stream().
						map(S->S.getSample()).
						filter(S->!StringUtils.isBlank(S)).
						findFirst().
						orElse(input);
				final double[] coverage_f = super.getCoverage(sr, queryInterval);
				final OptionalDouble od = super.getMedian(coverage_f);
				if(!od.isPresent() || od.getAsDouble()<=0.0) {
					LOG.error("median cov is 0 in "+input);
					return -1;
					}
				coverage_norm  = super.normalizeOnMedian(coverage_f, od.getAsDouble());
				}
			
			if(plotter!=null) {
				
				plotter.canvas.text(sampleName+" "+ queryInterval +" length:"+ StringUtils.niceInt(queryInterval.getLengthOnReference()) +" "+ input, 2, 12,FunctionalMap.of(Canvas.KEY_FILL,Color.BLACK,Canvas.KEY_STROKE,null,Canvas.KEY_FONT_SIZE,10));

				
				for(int i=0;i< boxplots.size();++i) {
					plotter.canvas.circle( plotter.coordX(i),plotter.coordY(coverage_norm[i]),0.5,FunctionalMap.of(Canvas.KEY_FILL,Color.BLACK,Canvas.KEY_STROKE_WIDTH,0.5));
					}
				}
			
			final List<CNV> cnvs = new ArrayList<>();
			int i=0;
			while(i< coverage_norm.length) {
				final BoxPlot bx = boxplots.get(i);
				int beg = i;
				int status =0;
				double sum=0;
				if( isDel(bx,coverage_norm[i])) {
					status = -1;
					while(i< coverage_norm.length && isDel(bx,coverage_norm[i])) {
						sum += coverage_norm[i];
						i++;
						}
					}
				else if( isDup(bx,coverage_norm[i])) {
					status = 1;
					while(i< coverage_norm.length && isDup(bx,coverage_norm[i])) {
						sum += coverage_norm[i];
						i++;
						}
					}
				if(status==0) {
					++i;
					continue;
					}
				final CNV cnv = new CNV();
				cnv.start = beg + queryInterval.getStart();
				cnv.end =  i + queryInterval.getStart();
				cnv.status = status;
				cnv.sum = sum;
				cnv.count = cnv.end - cnv.start;
				cnvs.add(cnv);
				}
			
			i=0;
			while(i+1< cnvs.size()) {
				final CNV cnv0 = cnvs.get(i  );
				final CNV cnv1 = cnvs.get(i+1);
				if(cnv0.status == cnv1.status && CoordMath.overlaps(
						cnv0.start-this.merge_adjacent, cnv0.end+this.merge_adjacent,
						cnv1.start, cnv1.end
						)) {
					LOG.debug("Merge "+cnv0+" "+cnv1);
					cnv0.start= Math.min(cnv0.start, cnv1.start);
					cnv0.end= Math.max(cnv0.end, cnv1.end);
					cnv0.sum += cnv1.sum;
					cnv0.count += cnv1.count;
					cnvs.remove(i+1);
					}
				else
					{
					i++;
					}
				}
			
			
			cnvs.removeIf(C->C.length() < this.min_cnv_size);
			
			
			if(plotter!=null) {
				for(CNV cnv: cnvs) {
					plotter.canvas.rect(
						plotter.coordX(cnv.start - queryInterval.getStart()) ,
						plotter.coordY(cnv.status==-1? 0.05:1.95),
						plotter.coordX(cnv.end -  queryInterval.getStart() )-plotter.coordX(cnv.start -  queryInterval.getStart()),
						5,
						FunctionalMap.of(Canvas.KEY_FILL,Color.MAGENTA)
						);
					}
				plotter.close();
				plotter=null;
				}
			
			try(VariantContextWriter w = this.writingVariantsDelegate.dictionary(dict).open(super.outputFile)) {
					final Set<VCFHeaderLine> metaData = new HashSet<>();
					VCFStandardHeaderLines.addStandardFormatLines(metaData, true, VCFConstants.DEPTH_KEY);
					VCFStandardHeaderLines.addStandardInfoLines(metaData, true, VCFConstants.ALLELE_COUNT_KEY);
					VCFStandardHeaderLines.addStandardInfoLines(metaData, true, VCFConstants.ALLELE_NUMBER_KEY);
					VCFStandardHeaderLines.addStandardInfoLines(metaData, true, VCFConstants.ALLELE_FREQUENCY_KEY);
					VCFStandardHeaderLines.addStandardFormatLines(metaData, true, VCFConstants.GENOTYPE_KEY);
					VCFStandardHeaderLines.addStandardInfoLines(metaData, true, VCFConstants.END_KEY);
					metaData.add( new VCFInfoHeaderLine("PREV",1,VCFHeaderLineType.Integer,"distance with previous"));
					metaData.add( new VCFInfoHeaderLine("SVLEN",1,VCFHeaderLineType.Integer,"SV LEN"));
					metaData.add( new VCFInfoHeaderLine("SVTYPE",1,VCFHeaderLineType.Integer,"SV TYPE"));
					metaData.add( new VCFFormatHeaderLine(FORMAT_NORM_DEPH,1,VCFHeaderLineType.Float,"Depth normalized on median"));
					
					final VCFHeader header = new VCFHeader(metaData,Collections.singletonList(sampleName));
					header.setSequenceDictionary(dict);
					JVarkitVersion.getInstance().addMetaData(this, header);
					w.writeHeader(header);
					final Allele DEL_ALLELE = Allele.create("<DEL>", false);
					final Allele DUP_ALLELE = Allele.create("<DUP>", false);
					
					int prev_pos=-1;
					for(CNV cnv: cnvs) {
						final Allele alt = (cnv.status==1?DUP_ALLELE:DEL_ALLELE);
						final double  norm = cnv.sum/cnv.count;
						final VariantContextBuilder vcb = new VariantContextBuilder(null,queryInterval.getContig(),cnv.start,cnv.end-1,Arrays.asList(Allele.REF_N,alt));
						vcb.attribute("SVTYPE", (cnv.status==1?"DUP":"DEL"));
						vcb.attribute(VCFConstants.END_KEY, cnv.end-1);
						vcb.attribute("SVLEN",cnv.length());
						vcb.attribute(VCFConstants.ALLELE_NUMBER_KEY, 2);
						
						if(prev_pos>0) vcb.attribute("PREV",prev_pos-cnv.start);
						prev_pos = cnv.end-1;
						
						List<Allele> alts;
						if(norm < 0.1 || norm> 1.9) {
							alts = Arrays.asList(alt,alt);
							vcb.attribute(VCFConstants.ALLELE_COUNT_KEY, 2);
							vcb.attribute(VCFConstants.ALLELE_FREQUENCY_KEY, 1.0);
							}
						else {
							alts = Arrays.asList(Allele.REF_N,alt);
							vcb.attribute(VCFConstants.ALLELE_COUNT_KEY, 1);
							vcb.attribute(VCFConstants.ALLELE_FREQUENCY_KEY, 0.5);
							}
						
						final GenotypeBuilder gb = new GenotypeBuilder(sampleName,alts);
						gb.attribute(FORMAT_NORM_DEPH,norm);
						vcb.genotypes(Arrays.asList(gb.make()));
						w.add(vcb.make());
						}
					}
			return 0;
			}
		catch(final Throwable err) {
			LOG.error(err);
			return -1;
			}
		}
	
	public static void main(final String[] args) {
		new CNVPaneOfNormal().instanceMainWithExit(args);
		}
}
