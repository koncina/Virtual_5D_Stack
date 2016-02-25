# Virtual 5D Stack

## Introduction

[Hyperstacks](http://rsbweb.nih.gov/ij/docs/guide/146-8.html#sub:Hyperstacks-Intro) enable ImageJ to handle multidimensional images up to 5D images (*width* x *height* x *slices* x *channels* x *time frames*). To avoid unnecessary duplication of raw files, this plugin enables the loading of multiple 3D files (*width* x *height* x *slices* x *channels*) as 5D hyperstacks.

** Important note: ** This plugin loads the complete stack of pictures into memory (while the notion of [virtual stack in Fiji/ImageJ](http://rsbweb.nih.gov/ij/docs/guide/146-8.html#sub:Virtual-Stacks) is referring to the on the fly loading of images to reduce memory load).

## Usage

The plugin expects a `.v5s` file which is actually a text file containing two lines (the dimension *t* which is limited to a size of 2 but will be unlimited in a future release).  
Each line contains an equal sized list of filenames separated by semi-columns (`;`) representing the dimension *z*. A missing file should be replaced by the keyword `empty` producing a black spacer image.  

An example of a `.v5s` file could look like:

```
A01.lsm;A02.lsm;A03.lsm;A04.lsm;A05.lsm
B01.lsm;B02.lsm;B03.lsm;empty;B05.lsm
```

where `A` and `B` represent two different levels of the dimension *t* and the numbers `01` to `05`, five different levels of the dimension *z*.
