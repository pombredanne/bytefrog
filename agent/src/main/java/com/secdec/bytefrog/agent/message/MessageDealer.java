/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.secdec.bytefrog.agent.message;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.secdec.bytefrog.common.message.MessageProtocol;
import com.secdec.bytefrog.common.queue.DataBufferOutputStream;

/**
 * An object that is responsible for sending data messages according to
 * protocol. Messages are written to buffers which are provided by a
 * {@link BufferService}, then sent via the same BufferService. For events that
 * require mapped ids for the current thread and method signature, those ids
 * (along with the appropriate secondary "map" events) will be automatically
 * generated by an internal {@link MethodId} and {@link ThreadId}. The time of
 * MessageFactory's construction will be saved, and used to calculate the
 * "relative timestamp" for each event that requires one.
 * 
 * @author dylanh
 */
public class MessageDealer
{
	private final MessageProtocol messageProtocol;
	private final BufferService bufferService;

	private final long startTime = System.currentTimeMillis();
	private final MethodId methodIdMapper = new MethodId();
	private final ExceptionId exceptionIdMapper = new ExceptionId();
	private final ThreadId threadIdMapper = new ThreadId();
	private final Sequencer sequencer = new Sequencer();

	/**
	 * 
	 * @param messageProtocol
	 * @param bufferService
	 */
	public MessageDealer(MessageProtocol messageProtocol, BufferService bufferService)
	{
		this.messageProtocol = messageProtocol;
		this.bufferService = bufferService;
	}

	// just a helper method, used internally
	protected int getTimeOffset()
	{
		return (int) (System.currentTimeMillis() - startTime);
	}

	/**
	 * Observes (returns) the next sequencer ID, without incrementing.
	 * 
	 * @returns the next sequencer ID
	 */
	public int getCurrentSequence()
	{
		return sequencer.observeSequence();
	}

	// ===============================
	// API METHODS:
	// ===============================

	/**
	 * MAP METHOD SIGNATURE (EVENT) MESSAGE
	 * 
	 * @param sig
	 * @param id
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMapMethodSignature(String sig, int id) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				messageProtocol.writeMapMethodSignature(buffer, id, sig);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * MAP EXCEPTION (EVENT) MESSAGE
	 * 
	 * @param exception
	 * @param id
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMapException(String exception, int id) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				messageProtocol.writeMapException(buffer, id, exception);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * MAP THREAD NAME (EVENT) MESSAGE
	 * 
	 * @param name
	 * @param id
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMapThreadName(String name, int id) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				messageProtocol.writeMapThreadName(buffer, id, getTimeOffset(), name);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * METHOD ENTRY (EVENT) MESSAGE
	 * 
	 * @param methodSig
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMethodEntry(String methodSig) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				int threadId = threadIdMapper.getCurrent();
				int methodId = methodIdMapper.getId(methodSig);
				messageProtocol.writeMethodEntry(buffer, timestamp, sequencer.getSequence(),
						methodId, threadId);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * METHOD EXIT (EVENT) MESSAGE
	 * 
	 * @param methodSig
	 * @param sourceLine
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMethodExit(String methodSig, int sourceLine) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				int threadId = threadIdMapper.getCurrent();
				int methodId = methodIdMapper.getId(methodSig);
				messageProtocol.writeMethodExit(buffer, timestamp, sequencer.getSequence(),
						methodId, sourceLine, threadId);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * EXCEPTION (EVENT) MESSAGE
	 * 
	 * @param exception
	 * @param methodSig
	 * @param sourceLine
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendException(String exception, String methodSig, int sourceLine)
			throws IOException, FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				int threadId = threadIdMapper.getCurrent();
				int methodId = methodIdMapper.getId(methodSig);
				int exceptionId = exceptionIdMapper.getId(exception);
				messageProtocol.writeException(buffer, timestamp, sequencer.getSequence(),
						methodId, exceptionId, sourceLine, threadId);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * EXCEPTION BUBBLE (EVENT) MESSAGE
	 * 
	 * @param exception
	 * @param methodSig
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendExceptionBubble(String exception, String methodSig) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				int threadId = threadIdMapper.getCurrent();
				int methodId = methodIdMapper.getId(methodSig);
				int exceptionId = exceptionIdMapper.getId(exception);
				messageProtocol.writeExceptionBubble(buffer, timestamp, sequencer.getSequence(),
						methodId, exceptionId, threadId);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * MARKER MESSAGE
	 * 
	 * @param key
	 * @param value
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMarker(String key, String value) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				messageProtocol.writeMarker(buffer, key, value, timestamp, sequencer.getSequence());
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	private class MethodId
	{
		private final AtomicInteger idGen = new AtomicInteger(1);
		private final ConcurrentMap<String, Integer> ids = new ConcurrentHashMap<>();

		public int getId(String methodSignature) throws IOException, FailedToObtainBufferException,
				FailedToSendBufferException
		{
			Integer id = ids.putIfAbsent(methodSignature, 0);
			if (id == null || id == 0)
			{
				id = idGen.getAndIncrement();
				if (ids.replace(methodSignature, 0, id))
				{
					sendMapMethodSignature(methodSignature, id);
				}
			}
			return ids.get(methodSignature);
		}
	}

	private class ExceptionId
	{
		private final AtomicInteger idGen = new AtomicInteger(1);
		private final ConcurrentMap<String, Integer> ids = new ConcurrentHashMap<>();

		public int getId(String exception) throws IOException, FailedToObtainBufferException,
				FailedToSendBufferException
		{
			Integer id = ids.putIfAbsent(exception, 0);
			if (id == null || id == 0)
			{
				id = idGen.getAndIncrement();
				if (ids.replace(exception, 0, id))
				{
					sendMapException(exception, id);
				}
			}
			return ids.get(exception);
		}
	}

	/**
	 * Creates a monotonically-incrementing unique id for each thread. The id
	 * for the currently-running thread is available via {@link #getCurrent()}
	 * 
	 * @author dylanh
	 * 
	 */
	private class ThreadId
	{
		/**
		 * Creates a new, unique id for each thread, on demand.
		 */
		private final ThreadLocal<Integer> threadId = new ThreadLocal<Integer>()
		{
			private final AtomicInteger uniqueId = new AtomicInteger(0);

			@Override
			protected Integer initialValue()
			{
				return uniqueId.getAndIncrement();
			};
		};

		/**
		 * Per-thread storage of whether or not a thread has a `threadId`
		 * association. The `getCurrent` method will perform a check and update
		 * using this.
		 */
		private final ThreadLocal<Boolean> threadHasId = new ThreadLocal<Boolean>()
		{
			@Override
			protected Boolean initialValue()
			{
				return false;
			};
		};

		/**
		 * Stores the latest known name of a thread. Updated by `getCurrent`.
		 */
		private final ThreadLocal<String> threadName = new ThreadLocal<String>()
		{
			@Override
			protected String initialValue()
			{
				return Thread.currentThread().getName();
			};
		};

		/**
		 * Get the unique id of the currently-running thread. When necessary,
		 * this method will automatically send a MapMethodName message to the
		 * message queue.
		 * 
		 * @return The unique id for the currently-running thread.
		 * @throws InterruptedException
		 * @throws IOException
		 * @throws FailedToSendBufferException
		 * @throws FailedToObtainBufferException
		 */
		public int getCurrent() throws IOException, FailedToObtainBufferException,
				FailedToSendBufferException
		{
			boolean updated = false;
			int id = threadId.get();

			// check if the id is "new"
			if (!threadHasId.get())
			{
				threadHasId.set(true);
				updated = true;
			}

			// check if the name has changed
			String oldName = threadName.get();
			if (oldName == null)
				oldName = "";
			String nowName = Thread.currentThread().getName();
			if (nowName == null)
				nowName = "";

			if (!nowName.equals(oldName))
			{
				threadName.set(nowName);
				updated = true;
			}

			// when updated, send a MapThreadName message
			if (updated)
			{
				sendMapThreadName(nowName, id);
			}
			return id;
		}
	}

	/**
	 * Provides an incrementing sequence counter for events.
	 * 
	 * @author RobertF
	 * 
	 */
	private class Sequencer
	{
		private final AtomicInteger sequenceId = new AtomicInteger();

		/**
		 * Get a new (unique) sequence identifier.
		 * 
		 * @return current sequence value
		 */
		public int getSequence()
		{
			return sequenceId.getAndIncrement();
		}

		/**
		 * Observes the current sequence identifier, without modifying it.
		 * 
		 * @return the next sequence value
		 */
		public int observeSequence()
		{
			return sequenceId.get();
		}
	}
}
