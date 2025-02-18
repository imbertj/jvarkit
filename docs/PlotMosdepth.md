# PlotMosdepth

![Last commit](https://img.shields.io/github/last-commit/lindenb/jvarkit.png)

Plot Mosdepth output


## Usage


This program is now part of the main `jvarkit` tool. See [jvarkit](JvarkitCentral.md) for compiling.


```
Usage: java -jar dist/jvarkit.jar plotmosdepth  [options] Files

Usage: plotmosdepth [options] Files
  Options:
    --format
      output format.
      Default: PDF
      Possible Values: [PDF, PNG, SVG]
    -h, --help
      print help and exit
    --helpFormat
      What kind of help. One of [usage,markdown,xml].
    --legend
      print legend
      Default: false
    --max-coverage
      Max coverage fwhen plotting (percent of bases / coverage). Ignore if 
      lower or equal to 0
      Default: -1
    -o, --output
      Output file. Optional . Default: stdout
    --prefix
      output prefix.
      Default: <empty string>
    --run-median
      runmed(coverage,'x') value for manhattan plot. Ignore if lower or equal 
      to 0
      Default: 5
    --version
      print version and exit

```


## Keywords

 * mosdepth



## Creation Date

20210621

## Source code 

[https://github.com/lindenb/jvarkit/tree/master/src/main/java/com/github/lindenb/jvarkit/tools/mosdepth/PlotMosdepth.java](https://github.com/lindenb/jvarkit/tree/master/src/main/java/com/github/lindenb/jvarkit/tools/mosdepth/PlotMosdepth.java)


## Contribute

- Issue Tracker: [http://github.com/lindenb/jvarkit/issues](http://github.com/lindenb/jvarkit/issues)
- Source Code: [http://github.com/lindenb/jvarkit](http://github.com/lindenb/jvarkit)

## License

The project is licensed under the MIT license.

## Citing

Should you cite **plotmosdepth** ? [https://github.com/mr-c/shouldacite/blob/master/should-I-cite-this-software.md](https://github.com/mr-c/shouldacite/blob/master/should-I-cite-this-software.md)

The current reference is:

[http://dx.doi.org/10.6084/m9.figshare.1425030](http://dx.doi.org/10.6084/m9.figshare.1425030)

> Lindenbaum, Pierre (2015): JVarkit: java-based utilities for Bioinformatics. figshare.
> [http://dx.doi.org/10.6084/m9.figshare.1425030](http://dx.doi.org/10.6084/m9.figshare.1425030)


## Example
```
$ find . -type f > jeter.list
$ java -jar ~/src/jvarkit-git/dist/plotmosdepth.jar --max-coverage 100 --prefix 20210622.mosdepth. --format png jeter.list | R --vanilla > /dev/null
```


