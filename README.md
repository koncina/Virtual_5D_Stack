# Virtual 5D Stack

## Introduction

[Hyperstacks](http://rsbweb.nih.gov/ij/docs/guide/146-8.html#sub:Hyperstacks-Intro) enable ImageJ to handle multidimensional images up to 5D images (*width* x *height* x *slices* x *channels* x *time frames*). To avoid unecessary duplication of raw files, this plugin enables the loading of multiple 3D files (*width* x *height* x *slices* x *channels*) as 5D hyperstacks.

## Usage

The plugin expects a `.v5s` file which is actually a text file containing two lines (the dimension $t$ which is limited to a size of 2 but will be unlimited in an upcoming version).  
Each line contains an equal sized list of filenames separated by a semi-column (`;`). If a file is missing and the rendering should be unbalanced, the filename can be replaced by the keyword `empty` which will produce a black image.  
An example of a `.v5s` file could look like:

```
A01.lsm;A02.lsm;A03.lsm;A04.lsm;B05.lsm
B01.lsm;B02.lsm;B03.lsm;empty;B05.lsm
```
## TODO

- [ ] Replace the `.v5s` text file by a more customizable xml file content.
- [ ] Remove the hard-coded dimension size limit of $t$.
- [ ] Add a checksum of the files within the xml file.
