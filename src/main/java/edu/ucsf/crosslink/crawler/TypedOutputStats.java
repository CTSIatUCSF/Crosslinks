package edu.ucsf.crosslink.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

public final class TypedOutputStats  {

	public enum OutputType { FOUND, AVOIDED, SKIPPED, PROCESSED, ERROR, DELETED};

	private OutputType type;
	private int limit = 100;
	private List<String> data = null;
	private AtomicInteger count = null;
	private AtomicLong elapsedTime = null;

	TypedOutputStats(OutputType type, int limit) {
		this.type = type;
		this.limit = limit;
		data = new ArrayList<String>();
		count = new AtomicInteger();
		elapsedTime = new AtomicLong();
	}
	
	public String getName() {
		return type.toString();
	}
	
	public String toString() {
		String retval = type.toString() + ": " + getCount();
		if (count.get() > 0 && elapsedTime.get() > 0) {
			retval += ", rate time/person = " + PeriodFormat.getDefault().print(new Period(elapsedTime.get()/getCount()));
		}
		return retval;
	}

	public synchronized void push(String t) {
		push(t, 0);
	}
	
	public synchronized void push(String t, long time) {
		count.incrementAndGet();
		elapsedTime.addAndGet(time);
		data.add(0, t);
		if (data.size() > limit) {
			data.remove(data.size() - 1);
		}
	}
	
	public int getCount() {
		return count.get();
	}

	public String getLatest() {
		return !data.isEmpty() ? data.get(0) : null;
	}
}
