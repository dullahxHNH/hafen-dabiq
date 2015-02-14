/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;

public abstract class BinCodec {
    private final static byte[] nil = new byte[0];
    protected int rh = 0, rt = 0, wh = 0, wt = 0;
    protected byte[] rbuf = nil, wbuf = nil;

    public static class BinError extends RuntimeException {
	public BinError(String message) {
	    super(message);
	}
    }
    public static class EOF extends BinError {
	public EOF(String message) {
	    super(message);
	}
    }
    public static class FormatError extends BinError {
	public FormatError(String message) {
	    super(message);
	}
    }

    protected abstract boolean underflow(int hint);

    private void rensure(int len) {
	while(len > rt - rh) {
	    if(!underflow(rh + len - rt))
		throw(new EOF("Required " + len + " bytes, got only " + (rt - rh)));
	}
    }
    private int rget(int len) {
	rensure(len);
	int co = rh;
	rh += len;
	return(co);
    }

    public boolean eom() {
	return((rh < rt) || underflow(1));
    }

    public int int8() {
	rensure(1);
	return(rbuf[rh++]);
    }
    public int uint8() {
	return(int8() & 0xff);
    }
    public int int16() {
	return(Utils.int16d(rbuf, rget(2)));
    }
    public int uint16() {
	return(Utils.uint16d(rbuf, rget(2)));
    }
    public int int32() {
	return(Utils.int32d(rbuf, rget(4)));
    }
    public long uint32() {
	return(Utils.uint32d(rbuf, rget(4)));
    }
    public long int64() {
	return(Utils.int64d(rbuf, rget(8)));
    }
    public String string() {
	int l = 0;
	while(true) {
	    if(l >= rt - rh) {
		if(!underflow(256))
		    throw(new EOF("Found no NUL (at length " + l + ")"));
	    }
	    if(rbuf[l + rh] == 0) {
		String ret = new String(rbuf, rh, l, Utils.utf8);
		rh += l + 1;
		return(ret);
	    }
	}
    }
    public byte[] bytes(int n) {
	byte[] ret = new byte[n];
	rensure(n);
	System.arraycopy(rbuf, rh, ret, 0, n);
	rh += n;
	return(ret);
    }
    public byte[] bytes() {
	while(underflow(65536));
	return(bytes(rt - rh));
    }
    public Coord coord() {
	return(new Coord(int32(), int32()));
    }
    public java.awt.Color color() {
	return(new java.awt.Color(uint8(), uint8(), uint8(), uint8()));
    }
    public float float32() {
	return(Utils.float32d(rbuf, rget(4)));
    }
    public double float64() {
	return(Utils.float64d(rbuf, rget(8)));
    }

    public Object[] list() {
	ArrayList<Object> ret = new ArrayList<Object>();
	list: while(true) {
	    if(eom())
		break;
	    int t = uint8();
	    switch(t) {
	    case Message.T_END:
		break list;
	    case Message.T_INT:
		ret.add(int32());
		break;
	    case Message.T_STR:
		ret.add(string());
		break;
	    case Message.T_COORD:
		ret.add(coord());
		break;
	    case Message.T_UINT8:
		ret.add(uint8());
		break;
	    case Message.T_UINT16:
		ret.add(uint16());
		break;
	    case Message.T_INT8:
		ret.add(int8());
		break;
	    case Message.T_INT16:
		ret.add(int16());
		break;
	    case Message.T_COLOR:
		ret.add(color());
		break;
	    case Message.T_TTOL:
		ret.add(list());
		break;
	    case Message.T_NIL:
		ret.add(null);
		break;
	    case Message.T_BYTES:
		int len = uint8();
		if((len & 128) != 0)
		    len = int32();
		ret.add(bytes(len));
		break;
	    case Message.T_FLOAT32:
		ret.add(float32());
		break;
	    case Message.T_FLOAT64:
		ret.add(float64());
		break;
	    default:
		throw(new FormatError("Encountered unknown type " + t + " in TTO list."));
	    }
	}
	return(ret.toArray());
    }

    protected abstract void overflow(int min);

    private void wensure(int len) {
	if(len < wt - wh)
	    overflow(len);
    }
    private int wget(int len) {
	wensure(len);
	int co = wh;
	wh += len;
	return(co);
    }

    public BinCodec addbytes(byte[] src, int off, int len) {
	wensure(len);
	System.arraycopy(src, off, wbuf, wh, len);
	return(this);
    }
    public BinCodec addbytes(byte[] src) {
	addbytes(src, 0, src.length);
	return(this);
    }
    public BinCodec adduint8(int num) {
	wbuf[wget(1)] = (byte)num;
	return(this);
    }
    public BinCodec adduint16(int num) {
	Utils.uint16e(num, wbuf, wget(2));
	return(this);
    }
    public BinCodec addint32(int num) {
	Utils.int32e(num, wbuf, wget(4));
	return(this);
    }
    public BinCodec adduint32(long num) {
	Utils.uint32e(num, wbuf, wget(4));
	return(this);
    }
    public BinCodec addstring2(String str) {
	addbytes(str.getBytes(Utils.utf8));
	return(this);
    }
    public BinCodec addstring(String str) {
	addstring2(str); adduint8(0);
	return(this);
    }
    public BinCodec addcoord(Coord c) {
	addint32(c.x); addint32(c.y);
	return(this);
    }

    public BinCodec addlist(Object... args) {
	for(Object o : args) {
	    if(o == null) {
		adduint8(Message.T_NIL);
	    } else if(o instanceof Integer) {
		adduint8(Message.T_INT);
		addint32(((Integer)o).intValue());
	    } else if(o instanceof String) {
		adduint8(Message.T_STR);
		addstring((String)o);
	    } else if(o instanceof Coord) {
		adduint8(Message.T_COORD);
		addcoord((Coord)o);
	    } else if(o instanceof byte[]) {
		byte[] b = (byte[])o;
		adduint8(Message.T_BYTES);
		if(b.length < 128) {
		    adduint8(b.length);
		} else {
		    adduint8(0x80);
		    addint32(b.length);
		}
		addbytes(b);
	    } else {
		throw(new RuntimeException("Cannot encode a " + o.getClass() + " as TTO"));
	    }
	}
	return(this);
    }
}