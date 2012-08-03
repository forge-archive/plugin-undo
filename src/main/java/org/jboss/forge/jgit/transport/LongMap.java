/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.transport;

/**
 * Simple Map<long,Object> helper for {@link PackParser}.
 *
 * @param <V>
 *            type of the value instance.
 */
final class LongMap<V> {
	private static final float LOAD_FACTOR = 0.75f;

	private Node<V>[] table;

	/** Number of entries currently in the map. */
	private int size;

	/** Next {@link #size} to trigger a {@link #grow()}. */
	private int growAt;

	LongMap() {
		table = createArray(64);
		growAt = (int) (table.length * LOAD_FACTOR);
	}

	boolean containsKey(final long key) {
		return get(key) != null;
	}

	V get(final long key) {
		for (Node<V> n = table[index(key)]; n != null; n = n.next) {
			if (n.key == key)
				return n.value;
		}
		return null;
	}

	V remove(final long key) {
		Node<V> n = table[index(key)];
		Node<V> prior = null;
		while (n != null) {
			if (n.key == key) {
				if (prior == null)
					table[index(key)] = n.next;
				else
					prior.next = n.next;
				size--;
				return n.value;
			}
			prior = n;
			n = n.next;
		}
		return null;
	}

	V put(final long key, final V value) {
		for (Node<V> n = table[index(key)]; n != null; n = n.next) {
			if (n.key == key) {
				final V o = n.value;
				n.value = value;
				return o;
			}
		}

		if (++size == growAt)
			grow();
		insert(new Node<V>(key, value));
		return null;
	}

	private void insert(final Node<V> n) {
		final int idx = index(n.key);
		n.next = table[idx];
		table[idx] = n;
	}

	private void grow() {
		final Node<V>[] oldTable = table;
		final int oldSize = table.length;

		table = createArray(oldSize << 1);
		growAt = (int) (table.length * LOAD_FACTOR);
		for (int i = 0; i < oldSize; i++) {
			Node<V> e = oldTable[i];
			while (e != null) {
				final Node<V> n = e.next;
				insert(e);
				e = n;
			}
		}
	}

	private final int index(final long key) {
		int h = ((int) key) >>> 1;
		h ^= (h >>> 20) ^ (h >>> 12);
		return h & (table.length - 1);
	}

	@SuppressWarnings("unchecked")
	private static final <V> Node<V>[] createArray(final int sz) {
		return new Node[sz];
	}

	private static class Node<V> {
		final long key;

		V value;

		Node<V> next;

		Node(final long k, final V v) {
			key = k;
			value = v;
		}
	}
}
