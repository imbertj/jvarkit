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
package com.github.lindenb.jvarkit.lang;

/** logical operators */
public enum LogicalOp
	{
	OR {
		@Override
		public boolean test(boolean b1, boolean b2)
			{
			return b1 || b2;
			}
	},
	AND {
	@Override
	public boolean test(boolean b1, boolean b2)
		{
		return b1 && b2;
		}
	},
	XOR /** exclusive OR */ /* le sherif , sherif de l'espace, XOR son domaine, c'est notre galaxiiie */{
	@Override
	public boolean test(boolean b1, boolean b2)
		{
		return b1 ^ b2;
		}
	},
	XNOR /** both are TRUE , or both are FALSE **/{
	@Override
	public boolean test(boolean b1, boolean b2)
		{
		return (b1 && b2) || (!b1 && !b2);
		}
	},
	NAND /** false only if both inputs are true **/{
	@Override
	public boolean test(boolean b1, boolean b2)
		{
		return !(b1 && b2);
		}
	};
	public abstract boolean test(boolean b1,boolean b2);
	}
