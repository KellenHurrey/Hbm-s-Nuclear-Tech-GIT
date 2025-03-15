package com.hbm.util;

public class Vector4i {
	public int x;
	public int y;
	public int z;
	public int w;

	public Vector4i set(int x, int y, int z, int w){
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
		return this;
	}
}
