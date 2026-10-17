package org.jmt.mcmt.paralelised.fastutil;

import java.util.Iterator;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListSet;

import it.unimi.dsi.fastutil.longs.AbstractLongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongSortedSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * A {@link LongSortedSet} backed by a {@link ConcurrentSkipListSet}, used in place of
 * {@code LongAVLTreeSet} where vanilla mutates and iterates a sorted set from several threads
 * (entity section ids). Iterators are weakly consistent and never throw
 * {@code ConcurrentModificationException}. Sub-set views share the backing set.
 */
public class ConcurrentLongSortedSet extends AbstractLongSortedSet {

	private final NavigableSet<Long> backing;

	public ConcurrentLongSortedSet() {
		this(new ConcurrentSkipListSet<>());
	}

	private ConcurrentLongSortedSet(NavigableSet<Long> backing) {
		this.backing = backing;
	}

	@Override public boolean add(long k) { return backing.add(k); }
	@Override public boolean remove(long k) { return backing.remove(k); }
	@Override public boolean contains(long k) { return backing.contains(k); }
	@Override public int size() { return backing.size(); }
	@Override public boolean isEmpty() { return backing.isEmpty(); }
	@Override public void clear() { backing.clear(); }

	@Override
	public LongComparator comparator() {
		return null; // natural order
	}

	@Override
	public long firstLong() {
		Long first = backing.isEmpty() ? null : backing.first();
		if (first == null) throw new NoSuchElementException();
		return first;
	}

	@Override
	public long lastLong() {
		Long last = backing.isEmpty() ? null : backing.last();
		if (last == null) throw new NoSuchElementException();
		return last;
	}

	@Override
	public LongBidirectionalIterator iterator() {
		return new ForwardIterator(backing.iterator());
	}

	@Override
	public LongBidirectionalIterator iterator(long fromElement) {
		return new ForwardIterator(backing.tailSet(fromElement, true).iterator());
	}

	@Override
	public LongSortedSet subSet(long fromElement, long toElement) {
		return new ConcurrentLongSortedSet(backing.subSet(fromElement, true, toElement, false));
	}

	@Override
	public LongSortedSet headSet(long toElement) {
		return new ConcurrentLongSortedSet(backing.headSet(toElement, false));
	}

	@Override
	public LongSortedSet tailSet(long fromElement) {
		return new ConcurrentLongSortedSet(backing.tailSet(fromElement, true));
	}

	/** Forward-only view over a boxed iterator; vanilla never walks these sets backwards. */
	private static final class ForwardIterator extends AbstractLongBidirectionalIterator {
		private final Iterator<Long> it;

		ForwardIterator(Iterator<Long> it) {
			this.it = it;
		}

		@Override public boolean hasNext() { return it.hasNext(); }
		@Override public long nextLong() { return it.next(); }
		@Override public boolean hasPrevious() { return false; }
		@Override public long previousLong() { throw new UnsupportedOperationException("forward-only iterator"); }
		@Override public void remove() { it.remove(); }
	}
}
