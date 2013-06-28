package com.google.code.rapid.queue.util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.junit.Test;

import cn.org.rapid_framework.test.util.MultiThreadTestUtils;

public class KryoUtilTest extends Assert{

	public static class KryoUserBean {
		private String username;
		private String password;
	}
	
	File file = new File(FileUtils.getTempDirectory(),"kryo_test");
	@Test
	public void testSerDser() throws IOException {
		KryoUserBean b = new KryoUserBean();
		b.username = "badqiu";
		b.password = RandomStringUtils.randomAlphabetic(4096);
		
		byte[] bytes = KryoUtil.toBytes(b, 1024);
		FileUtils.writeByteArrayToFile(file, bytes);
		
		toBytesPerf(b);
		fromBytesPerf(bytes);
	}

	private void toBytesPerf(KryoUserBean bean) {
		long start = System.currentTimeMillis();
		int count = 10000;
		for(int i = 0; i < count; i++) {
			byte[] bytes = KryoUtil.toBytes(bean, 4096 + 500);
		}
		long cost = System.currentTimeMillis() - start;
		printTps("4096 byte dser KryoUtil.toBytesPerf", count, cost, 1);
	}

	private void fromBytesPerf(byte[] bytes) {
		long start = System.currentTimeMillis();
		int count = 100000;
		for(int i = 0; i < count; i++) {
			KryoUtil.fromBytes(bytes, KryoUserBean.class);
		}
		long cost = System.currentTimeMillis() - start;
		printTps("4096 byte dser KryoUtil.fromBytes", count, cost, 1);
	}
	

	@Test
	public void testRead() throws IOException {
		byte[] fromBytes = FileUtils.readFileToByteArray(file);
		KryoUserBean fromBean = KryoUtil.fromBytes(fromBytes, KryoUserBean.class);
		assertEquals(fromBean.username,"badqiu");
		assertEquals(fromBean.password,"pwd");
	}
	
	@Test
	public void test() throws InterruptedException {

		for (final int threadCount : new int[] { 2, 8, 16, 30 }) {
			for (final int count : new int[] { 500000 }) {
				exec(threadCount, count);
			}
		}

	}

	private void exec(final int threadCount, final int count)
			throws InterruptedException {
		long cost = MultiThreadTestUtils.executeAndWait(threadCount,
				new Runnable() {
					@Override
					public void run() {
						for (int i = 0; i < count; i++) {
							KryoUtil.toBytes("1", 10);
						}
					}
				});
		printTps("info,threadCount:" + threadCount, count, cost, threadCount);
	}

	private static void printTps(String info, int count, long cost,
			int concurrency) {
		int allTps = (int) (count * 1000.0 / cost);
		System.out.println(info + " cost:" + cost + " count:" + count
				+ " all_tps:" + allTps + " per_thread_tps:"
				+ (allTps / concurrency));
	}

//	public void runSimpleObjectPoolPerf(final int count, final int concurrency)
//			throws Exception {
//		final SimpleObjectPool<Object> objectPool = new SimpleObjectPool<Object>(
//				1000, new ObjectFactory<Object>() {
//					@Override
//					public Object makeObject() throws Exception {
//						return new Object();
//					}
//				});
//
//		Runnable task = new Runnable() {
//			public void run() {
//				for (int i = 0; i < count; i++) {
//					try {
//						Object obj = objectPool.borrowObject();
//						objectPool.returnObject(obj);
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
//				}
//			}
//		};
//
//		runPerf("runSimpleObjectPoolPerf,concurrency:" + concurrency, count,
//				concurrency, task);
//	}

	public void runObjectPoolPerf(final int count, final int concurrency)
			throws Exception {
		BasePoolableObjectFactory<Object> poolableObjectFactory = new BasePoolableObjectFactory<Object>() {
			@Override
			public Object makeObject() throws Exception {
				return new Object();
			}
		};
		final GenericObjectPool objectPool = new GenericObjectPool<Object>(
				poolableObjectFactory, 2000,
				GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION,
				GenericObjectPool.DEFAULT_MAX_WAIT, 2000);

		Runnable task = new Runnable() {
			public void run() {
				for (int i = 0; i < count; i++) {
					try {
						Object obj = objectPool.borrowObject();
						objectPool.returnObject(obj);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};

		runPerf("ObjectPoolPerf,concurrency:" + concurrency, count,
				concurrency, task);
	}

	private void runPerf(final String logInfo, final int count,
			final int concurrency, Runnable task) throws InterruptedException {
		long start = System.currentTimeMillis();
		executeAndWait(concurrency, task);
		printTps(logInfo, count, start, concurrency);
	}

	private synchronized void executeAndWait(final int concurrency,
			final Runnable task) throws InterruptedException {
		final AtomicInteger doneCount = new AtomicInteger(0);
		final AtomicInteger startCount = new AtomicInteger(0);
		Runnable delegate = new Runnable() {
			@Override
			public void run() {
				// System.out.println("task_start,startCount:"+
				// startCount.incrementAndGet()+" concurrency:"+concurrency+" doneCount:"+doneCount);
				task.run();
				// System.out.println("task_done,doneCount:"+
				// doneCount.incrementAndGet()+" concurrency:"+concurrency);
			}
		};
		MultiThreadTestUtils.executeAndWait(concurrency, delegate);
	}
}
