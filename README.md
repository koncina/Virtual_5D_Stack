# Virtual 5D Stack

## Introduction

[Hyperstacks](http://rsbweb.nih.gov/ij/docs/guide/146-8.html#sub:Hyperstacks-Intro) enable ImageJ to handle multidimensional images up to 5D images (*width* x *height* x *channels* x *slices* x *frames* *i.e.* xyczt). To avoid unnecessary duplication of raw files, this plugin allows to load multiple 3D files (*width* x *height* x *slices* x *channels*) as 5D hyperstacks.

## Usage

### Create new Virtual 5D Stack

Create a new stack using the `File > New > Virtual 5D Stack` command. Select by which dimension you would like to load the image files (`t` or `z`) and fill in the appropriate size. For each `t` (or `z`) level, load the images to populate the remaining dimension.

The plugin:
- Increases the canvas size to the maximum image size in the set.
- Populates missing images as black empty images labelled `missing`.

### Sort Virtual 5D Stacks

When loading a `Virtual 5D Stack`, you might want to sort the images along the `z` dimension. Use the `Plugins > Virtual 5D Stack > Sort V5s` to create a montage and drag and drop the images to swap their position. You can also drag an image outside the canvas (left or right side) to increase the dimension. To remove a specific `z` level, drag an image outside the canvas (top or bottom side). A dialog window is shown in order to confirm the suppression of the level (no dialog is shown if the level is empty, _i.e._ contains only `missing` images).

### Save the Virtual 5D Stack

Use the command `File > Save as > Virtual 5D Stack ...` to save the `v5s` file. A `v5s` is a xml file containing the informations to reload the 5D hyperstack as it is displayed from the individual raw images.

### ROI

It is possible to store regions of interest (ROI) directly in a `v5s` file `Plugins > Virtual 5D Stack > ROI > Store ROI`. The plugin will ask to attribute a name to the ROI set. Actually, it is only possible to store a single ROI for each slice. To reload a ROI, use the command `Plugins > Virtual 5D Stack > ROI > Load ROI`. The advantage of storing ROIs in a `v5s` file is to keep the ROI in sync when using the `Sort V5s` plugin.
