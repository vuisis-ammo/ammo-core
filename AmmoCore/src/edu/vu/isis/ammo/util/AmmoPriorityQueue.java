package edu.vu.isis.ammo.util;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import edu.vu.isis.ammo.core.InstrumentedQueue;

/**
 * The standard priority queue has some issues.
 * - it has no instrumentation
 * - it is an unlimited size
 * 
 * This will make use of the decorator pattern.
 */

public class AmmoPriorityQueue<E> 
  implements 
      Serializable, 
      Iterable<E>, 
      Collection<E>, 
      BlockingQueue<E>, // when extracting things from the queue the thread will block when the queue is empty
      Queue<E>, 
      InstrumentedQueue 
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 330074004807488031L;

	// INSTRUMENTATION
	
	@Override
	public int inputBottleneck() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int outputBottleneck() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	// LIMIT NUMBER OF ITEMS IN THE QUEUE
	
	/**
	 * There is a problem with extending Priority Blocking Queue.
	 * The locks used are not available so we need to be "too clever".
	 * We will introduce some decorator methods using java reflection to gain access.
	 */
	
	private final ReentrantLock lock; // = new ReentrantLock();
	private final Condition notFull;

	
	/**
	 * Creates a LinkedBlockingQueue with the given (fixed) capacity.
	 * Parameters:
	 *   capacity - the capacity of this queue
	 * Throws:
	 *   IllegalArgumentException - if capacity is not greater than zero
	 */
	final private int capacity;
	final private PriorityBlockingQueue<E> queue;
	
	public AmmoPriorityQueue(int capacity) throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
		if (capacity < 1) throw new IllegalArgumentException("capacity must be greater than zero");		
		this.capacity = capacity;
		this.queue = new PriorityBlockingQueue<E>();
		
		// gaining access to private field
		Field reqField;
		try {
			reqField = PriorityBlockingQueue.class.getDeclaredField("lock");
			reqField.setAccessible(true);
			this.lock = (ReentrantLock)reqField.get(ReentrantLock.class);
			this.notFull = this.lock.newCondition();
			
		} catch (SecurityException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			throw ex;
		} catch (NoSuchFieldException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			throw ex;
		} catch (IllegalAccessException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			throw ex;
		}
	}
	
	/**
	 * Returns the number of additional elements that this queue can ideally 
	 * (in the absence of memory or resource constraints) accept without blocking. 
	 * This is always equal to the initial capacity of this queue less the current size of this queue.
	 * 
	 * Note that you cannot always tell if an attempt to insert an element will succeed by inspecting 
	 * remainingCapacity because it may be the case that another thread is about to insert or remove an element.	
	 */
	public int remainingCapacity() {
	     return this.capacity - this.size();
	}

	@Override
	public Iterator<E> iterator() {
		return this.queue.iterator();
	}

	@Override
	public boolean add(E object) {
		return this.queue.add(object);
	}

	@Override
	public boolean addAll(Collection<? extends E> arg0) {
		return this.queue.addAll(arg0);
	}

	@Override
	public void clear() {
		this.queue.clear();
	}

	@Override
	public boolean contains(Object object) {
		return this.queue.contains(object);
	}

	@Override
	public boolean containsAll(Collection<?> arg0) {
		return this.queue.containsAll(arg0);
	}

	@Override
	public boolean isEmpty() {
		return this.queue.isEmpty();
	}

	@Override
	public boolean remove(Object object) {
		return this.queue.remove(object);
	}

	@Override
	public boolean removeAll(Collection<?> arg0) {
		return this.queue.removeAll(arg0);
	}

	@Override
	public boolean retainAll(Collection<?> arg0) {
		return this.queue.retainAll(arg0);
	}

	@Override
	public int size() {
		return this.queue.size();
	}

	@Override
	public Object[] toArray() {
		return this.queue.toArray();
	}

	@Override
	public <T> T[] toArray(T[] array) {
		return this.queue.toArray(array);
	}

	@Override
	public int drainTo(Collection<? super E> arg0) {
		return this.queue.drainTo(arg0);
	}

	@Override
	public int drainTo(Collection<? super E> arg0, int arg1) {
		return this.queue.drainTo(arg0, arg1);
	}

	/**
	 * All methods which write to the queue call offer.
	 * Therefore this is the only change necessary.
	 * 
	 * @param e
	 * @return
	 */
	@Override
	public boolean offer(E e) {
		this.lock.lock();
		try {
			while (this.size() == this.capacity)
				notFull.await();
			boolean success = this.queue.offer(e);
			return success;
		} catch (InterruptedException ie) {
			notFull.signal(); // propagate to a non-interrupted thread
			return false;

		} finally {
			this.lock.unlock();
		}
	}

	@Override
	public boolean offer(E e, long timeout, TimeUnit unit)
			throws InterruptedException {
		return this.queue.offer(e, timeout, unit);
	}

	@Override
	public E poll(long timeout, TimeUnit unit) throws InterruptedException {
		return this.queue.poll(timeout, unit);
	}

	@Override
	public void put(E e) throws InterruptedException {
		this.queue.put(e);
	}

	@Override
	public E take() throws InterruptedException {
		return this.queue.take();
	}

	@Override
	public E element() {
		return this.queue.element();
	}

	@Override
	public E peek() {
		return this.queue.peek();
	}

	@Override
	public E poll() {
		return this.queue.poll();
	}

	@Override
	public E remove() {
		return this.queue.remove();
	}


}
