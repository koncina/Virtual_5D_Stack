package eu.koncina.ij.V5S;

import ij.IJ;

public class V5sPosition {
	int c = 0;
	int z = 0;
	int t = 0;

	public V5sPosition() {

	}

	public V5sPosition(int c, int z, int t) {
		this.c = c;
		this.z = z;
		this.t = t;
	}

	public V5sPosition(int z, int t) {
		this.z = z;
		this.t = t;
	}

	public int getC() {
		return this.c;
	}

	public int getZ() {
		return this.z;
	}

	public int getT() {
		return this.t;
	}

	public void setPosition(int c, int z, int t) {
		this.c = c;
		this.z = z;
		this.t = t;
	}

	public void setZ(int z) {
		this.z = z;
	}
	
	public void setT(int t) {
		this.t = t;
	}

	public boolean equalsZT(Object obj) {
		if (!(obj instanceof V5sPosition))
			return false;
		V5sPosition p = (V5sPosition) obj;
		if (this.z == p.getZ() && this.t == p.getT())
			return true;
		return false;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof V5sPosition)) return false;
		if (obj == this) return true;
		V5sPosition p = (V5sPosition) obj;
		if (this.c == p.getC() && this.z == p.getZ() && this.t == p.getT())
			return true;
		return false;
	}

}