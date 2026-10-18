package org.jmt.mcmt.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcurrentCollections {

	private static final Logger LOGGER = LoggerFactory.getLogger("MCMT");
	
	public static <T> Set<T> newHashSet() {
		LOGGER.debug("Concurrent hash set created");
		return Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());
	}
	
	public static <T, U> Map<T, U> newHashMap() {
		LOGGER.debug("Concurrent hash map created");
		return new ConcurrentHashMap<T, U>();
	}
	
	public static <T> List<T> newLinkedList() {
		LOGGER.debug("Concurrent \"linked\" list created");
		return new CopyOnWriteArrayList<T>();
	}
	
	public static <T> Collector<T, ?, List<T>> toList() {
		return Collectors.toCollection(CopyOnWriteArrayList::new);
    }
	
	public static <T> Queue<T> newArrayDeque() {
		LOGGER.debug("Concurrent \"array\" deque created");
		return new ConcurrentLinkedDeque<T>();
	}
	
}
