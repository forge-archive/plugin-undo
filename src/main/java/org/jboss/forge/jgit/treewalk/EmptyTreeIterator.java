/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.treewalk;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

/** Iterator over an empty tree (a directory with no files). */
public class EmptyTreeIterator extends AbstractTreeIterator {
	/** Create a new iterator with no parent. */
	public EmptyTreeIterator() {
		// Create a root empty tree.
	}

	EmptyTreeIterator(final AbstractTreeIterator p) {
		super(p);
		pathLen = pathOffset;
	}

	/**
	 * Create an iterator for a subtree of an existing iterator.
	 * <p>
	 * The caller is responsible for setting up the path of the child iterator.
	 *
	 * @param p
	 *            parent tree iterator.
	 * @param childPath
	 *            path array to be used by the child iterator. This path must
	 *            contain the path from the top of the walk to the first child
	 *            and must end with a '/'.
	 * @param childPathOffset
	 *            position within <code>childPath</code> where the child can
	 *            insert its data. The value at
	 *            <code>childPath[childPathOffset-1]</code> must be '/'.
	 */
	public EmptyTreeIterator(final AbstractTreeIterator p,
			final byte[] childPath, final int childPathOffset) {
		super(p, childPath, childPathOffset);
		pathLen = childPathOffset - 1;
	}

	@Override
	public AbstractTreeIterator createSubtreeIterator(final ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		return new EmptyTreeIterator(this);
	}

	@Override
	public boolean hasId() {
		return false;
	}

	@Override
	public ObjectId getEntryObjectId() {
		return ObjectId.zeroId();
	}

	@Override
	public byte[] idBuffer() {
		return zeroid;
	}

	@Override
	public int idOffset() {
		return 0;
	}

	@Override
	public void reset() {
		// Do nothing.
	}

	@Override
	public boolean first() {
		return true;
	}

	@Override
	public boolean eof() {
		return true;
	}

	@Override
	public void next(final int delta) throws CorruptObjectException {
		// Do nothing.
	}

	@Override
	public void back(final int delta) throws CorruptObjectException {
		// Do nothing.
	}

	@Override
	public void stopWalk() {
		if (parent != null)
			parent.stopWalk();
	}
}
