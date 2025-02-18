# VcfStats2

![Last commit](https://img.shields.io/github/last-commit/lindenb/jvarkit.png)

Produce VCF statitics


## Usage


This program is now part of the main `jvarkit` tool. See [jvarkit](JvarkitCentral.md) for compiling.


```
Usage: java -jar dist/jvarkit.jar vcfstats2  [options] Files

Usage: vcfstats2 [options] Files
  Options:
    --bean
      main bean id in the XML configuration
      Default: main
    --description
      main section description
      Default: <empty string>
    -h, --help
      print help and exit
    --helpFormat
      What kind of help. One of [usage,markdown,xml].
    --list
      list available modules and exit
    --other-samples
      if sample doesn't belong to a population in sample2pop, create a new 
      population named 'x' and insert those lonely samples in that population
      Default: other
  * -o, --output
      output directory
    --pipe
      write input VCF to stdout
      Default: false
    --prefix
      prefix for output files
      Default: vcfstats.
    --sample2population, --sample2pop
      tab delimited file containing (sample-name)(TAB)(collection-name). Empty 
      lines or starting with '#' are skipped
    --xml, --spring
      XML Spring config
    --title
      main section title
      Default: <empty string>
    --version
      print version and exit

```


## Keywords

 * vcf
 * stats
 * multiqc



## Creation Date

20131212

## Source code 

[https://github.com/lindenb/jvarkit/tree/master/src/main/java/com/github/lindenb/jvarkit/tools/vcfstats/VcfStats2.java](https://github.com/lindenb/jvarkit/tree/master/src/main/java/com/github/lindenb/jvarkit/tools/vcfstats/VcfStats2.java)


## Contribute

- Issue Tracker: [http://github.com/lindenb/jvarkit/issues](http://github.com/lindenb/jvarkit/issues)
- Source Code: [http://github.com/lindenb/jvarkit](http://github.com/lindenb/jvarkit)

## License

The project is licensed under the MIT license.

## Citing

Should you cite **vcfstats2** ? [https://github.com/mr-c/shouldacite/blob/master/should-I-cite-this-software.md](https://github.com/mr-c/shouldacite/blob/master/should-I-cite-this-software.md)

The current reference is:

[http://dx.doi.org/10.6084/m9.figshare.1425030](http://dx.doi.org/10.6084/m9.figshare.1425030)

> Lindenbaum, Pierre (2015): JVarkit: java-based utilities for Bioinformatics. figshare.
> [http://dx.doi.org/10.6084/m9.figshare.1425030](http://dx.doi.org/10.6084/m9.figshare.1425030)


## Example

```
java -jar dist/jvarkit.jar vcfstats src/test/resources/rotavirus_rf.unifiedgenotyper.vcf.gz |  R --no-save 
```


