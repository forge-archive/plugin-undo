/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.lib;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.util.IO;

/**
 * Base class for a set of loaders for different representations of Git objects.
 * New loaders are constructed for every object.
 */
public abstract class ObjectLoader {
	/**
	 * @return Git in pack object type, see {@link Constants}.
	 */
	public abstract int getType();

	/**
	 * @return size of object in bytes
	 */
	public abstract long getSize();

	/**
	 * @return true if this object is too large to obtain as a byte array.
	 *         Objects over a certain threshold should be accessed only by their
	 *         {@link #openStream()} to prevent overflowing the JVM heap.
	 */
	public boolean isLarge() {
		try {
			getCachedBytes();
			return false;
		} catch (LargeObjectException tooBig) {
			return true;
		}
	}

	/**
	 * Obtain a copy of the bytes of this object.
	 * <p>
	 * Unlike {@link #getCachedBytes()} this method returns an array that might
	 * be modified by the caller.
	 *
	 * @return the bytes of this object.
	 * @throws LargeObjectException
	 *             if the object won't fit into a byte array, because
	 *             {@link #isLarge()} returns true. Callers should use
	 *             {@link #openStream()} instead to access the contents.
	 */
	public final byte[] getBytes() throws LargeObjectException {
		return cloneArray(getCachedBytes());
	}

	/**
	 * Obtain a copy of the bytes of this object.
	 *
	 * If the object size is less than or equal to {@code sizeLimit} this method
	 * will provide it as a byte array, even if {@link #isLarge()} is true. This
	 * utility is useful for application code that absolutely must have the
	 * object as a single contiguous byte array in memory.
	 *
	 * Unlike {@link #getCachedBytes(int)} this method returns an array that
	 * might be modified by the caller.
	 *
	 * @param sizeLimit
	 *            maximum number of bytes to return. If the object is larger
	 *            than this limit, {@link LargeObjectException} will be thrown.
	 * @return the bytes of this object.
	 * @throws LargeObjectException
	 *             if the object is bigger than {@code sizeLimit}, or if
	 *             {@link OutOfMemoryError} occurs during allocation of the
	 *             result array. Callers should use {@link #openStream()}
	 *             instead to access the contents.
	 * @throws MissingObjectException
	 *             the object is large, and it no longer exists.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	public final byte[] getBytes(int sizeLimit) throws LargeObjectException,
			MissingObjectException, IOException {
		byte[] cached = getCachedBytes(sizeLimit);
		try {
			return cloneArray(cached);
		} catch (OutOfMemoryError tooBig) {
			throw new LargeObjectException.OutOfMemory(tooBig);
		}
	}

	/**
	 * Obtain a reference to the (possibly cached) bytes of this object.
	 * <p>
	 * This method offers direct access to the internal caches, potentially
	 * saving on data copies between the internal cache and higher level code.
	 * Callers who receive this reference <b>must not</b> modify its contents.
	 * Changes (if made) will affect the cache but not the repository itself.
	 *
	 * @return the cached bytes of this object. Do not modify it.
	 * @throws LargeObjectException
	 *             if the object won't fit into a byte array, because
	 *             {@link #isLarge()} returns true. Callers should use
	 *             {@link #openStream()} instead to access the contents.
	 */
	public abstract byte[] getCachedBytes() throws LargeObjectException;

	/**
	 * Obtain a reference to the (possibly cached) bytes of this object.
	 *
	 * If the object size is less than or equal to {@code sizeLimit} this method
	 * will provide it as a byte array, even if {@link #isLarge()} is true. This
	 * utility is useful for application code that absolutely must have the
	 * object as a single contiguous byte array in memory.
	 *
	 * This method offers direct access to the internal caches, potentially
	 * saving on data copies between the internal cache and higher level code.
	 * Callers who receive this reference <b>must not</b> modify its contents.
	 * Changes (if made) will affect the cache but not the repository itself.
	 *
	 * @param sizeLimit
	 *            maximum number of bytes to return. If the object size is
	 *            larger than this limit and {@link #isLarge()} is true,
	 *            {@link LargeObjectException} will be thrown.
	 * @return the cached bytes of this object. Do not modify it.
	 * @throws LargeObjectException
	 *             if the object is bigger than {@code sizeLimit}, or if
	 *             {@link OutOfMemoryError} occurs during allocation of the
	 *             result array. Callers should use {@link #openStream()}
	 *             instead to access the contents.
	 * @throws MissingObjectException
	 *             the object is large, and it no longer exists.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	public byte[] getCachedBytes(int sizeLimit) throws LargeObjectException,
			MissingObjectException, IOException {
		if (!isLarge())
			return getCachedBytes();

		ObjectStream in = openStream();
		try {
			long sz = in.getSize();
			if (sizeLimit < sz)
				throw new LargeObjectException.ExceedsLimit(sizeLimit, sz);

			if (Integer.MAX_VALUE < sz)
				throw new LargeObjectException.ExceedsByteArrayLimit();

			byte[] buf;
			try {
				buf = new byte[(int) sz];
			} catch (OutOfMemoryError notEnoughHeap) {
				throw new LargeObjectException.OutOfMemory(notEnoughHeap);
			}

			IO.readFully(in, buf, 0, buf.length);
			return buf;
		} finally {
			in.close();
		}
	}

	/**
	 * Obtain an input stream to read this object's data.
	 *
	 * @return a stream of this object's data. Caller must close the stream when
	 *         through with it. The returned stream is buffered with a
	 *         reasonable buffer size.
	 * @throws MissingObjectException
	 *             the object no longer exists.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	public abstract ObjectStream openStream() throws MissingObjectException,
			IOException;

	/**
	 * Copy this object to the output stream.
	 * <p>
	 * For some object store implementations, this method may be more efficient
	 * than reading from {@link #openStream()} into a temporary byte array, then
	 * writing to the destination stream.
	 * <p>
	 * The default implementation of this method is to copy with a temporary
	 * byte array for large objects, or to pass through the cached byte array
	 * for small objects.
	 *
	 * @param out
	 *            stream to receive the complete copy of this object's data.
	 *            Caller is responsible for flushing or closing this stream
	 *            after this method returns.
	 * @throws MissingObjectException
	 *             the object no longer exists.
	 * @throws IOException
	 *             the object store cannot be accessed, or the stream cannot be
	 *             written to.
	 */
	public void copyTo(OutputStream out) throws MissingObjectException,
			IOException {
		if (isLarge()) {
			ObjectStream in = openStream();
			try {
				final long sz = in.getSize();
				byte[] tmp = new byte[8192];
				long copied = 0;
				while (copied < sz) {
					int n = in.read(tmp);
					if (n < 0)
						throw new EOFException();
					out.write(tmp, 0, n);
					copied += n;
				}
				if (0 <= in.read())
					throw new EOFException();
			} finally {
				in.close();
			}
		} else {
			out.write(getCachedBytes());
		}
	}

	private static byte[] cloneArray(final byte[] data) {
		final byte[] copy = new byte[data.length];
		System.arraycopy(data, 0, copy, 0, data.length);
		return copy;
	}

	/**
	 * Simple loader around the cached byte array.
	 * <p>
	 * ObjectReader implementations can use this stream type when the object's
	 * content is small enough to be accessed as a single byte array.
	 */
	public static class SmallObject extends ObjectLoader {
		private final int type;

		private final byte[] data;

		/**
		 * Construct a small object loader.
		 *
		 * @param type
		 *            type of the object.
		 * @param data
		 *            the object's data array. This array will be returned as-is
		 *            for the {@link #getCachedBytes()} method.
		 */
		public SmallObject(int type, byte[] data) {
			this.type = type;
			this.data = data;
		}

		@Override
		public int getType() {
			return type;
		}

		@Override
		public long getSize() {
			return getCachedBytes().length;
		}

		@Override
		public boolean isLarge() {
			return false;
		}

		@Override
		public byte[] getCachedBytes() {
			return data;
		}

		@Override
		public ObjectStream openStream() {
			return new ObjectStream.SmallStream(this);
		}
	}
}
