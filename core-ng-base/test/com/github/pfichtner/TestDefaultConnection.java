package com.github.pfichtner;

import static com.github.pfichtner.Pin.analogPin;
import static com.github.pfichtner.Pin.digitalPin;
import static com.github.pfichtner.hamcrest.EventMatchers.eventFor;
import static com.github.pfichtner.proto.impl.ALProtoBuilder.alpProtocolMessage;
import static com.github.pfichtner.proto.impl.ALProtoBuilder.ALPProtocolKey.ANALOG_PIN_READ;
import static com.github.pfichtner.proto.impl.ALProtoBuilder.ALPProtocolKey.DIGITAL_PIN_READ;
import static java.lang.Integer.MAX_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.github.pfichtner.Connection.ListenerAdapter;
import com.github.pfichtner.events.AnalogPinValueChangedEvent;
import com.github.pfichtner.events.DigitalPinValueChangedEvent;
import com.github.pfichtner.events.EventListenerAdapter;
import com.github.pfichtner.events.FilteredEventListenerAdapter;
import com.github.pfichtner.proto.impl.ArdulinkProtocol;

public class TestDefaultConnection {

	private static final int TIMEOUT = 5 * 1000;
	private static final ArdulinkProtocol AL_PROTO = new ArdulinkProtocol();

	private PipedOutputStream arduinosOutputStream;
	private final ByteArrayOutputStream os = new ByteArrayOutputStream();
	private Connection connection;
	private Link link;
	private final AtomicInteger bytesRead = new AtomicInteger();

	@Before
	public void setup() throws IOException {
		PipedInputStream pis = new PipedInputStream();
		this.arduinosOutputStream = new PipedOutputStream(pis);
		this.connection = new DefaultConnection(pis, os);
		this.link = new Link(connection, AL_PROTO) {
			@Override
			protected void received(byte[] bytes) {
				super.received(bytes);
				TestDefaultConnection.this.bytesRead.addAndGet(bytes.length);
			}
		};
	}

	@Test(timeout = TIMEOUT)
	public void canSendAnalogValue() throws IOException {
		int pin = anyPositive(int.class);
		int value = anyPositive(int.class);
		this.link.switchAnalogPin(analogPin(pin), value);
		assertThat(toArduinoWasSent(), is("alp://ppin/" + pin + "/" + value
				+ "\n"));
	}

	@Test(timeout = TIMEOUT)
	public void canSendDigitalValue() throws IOException {
		int pin = anyPositive(int.class);
		this.link.switchDigitalPin(digitalPin(pin), true);
		assertThat(toArduinoWasSent(), is("alp://ppsw/" + pin + "/1\n"));
	}

	// TODO Test for invalid payloads

	@Test(timeout = TIMEOUT)
	public void doesSendStartListeningAnalogCommangToArduino()
			throws IOException {
		int pin = anyPositive(int.class);
		this.link.addListener(new FilteredEventListenerAdapter(analogPin(pin),
				null));
		assertThat(toArduinoWasSent(), is("alp://srla/" + pin + "\n"));
	}

	@Test(timeout = TIMEOUT)
	public void doesSendStopListeningAnalogCommangToArduino()
			throws IOException {
		int pin = anyPositive(int.class);
		FilteredEventListenerAdapter l1 = new FilteredEventListenerAdapter(
				analogPin(pin), null);
		FilteredEventListenerAdapter l2 = new FilteredEventListenerAdapter(
				analogPin(pin), null);
		this.link.addListener(l1);
		this.link.addListener(l2);
		String m1 = "alp://srla/" + pin + "\n";
		assertThat(toArduinoWasSent(), is(m1 + m1));
		this.link.removeListener(l1);
		this.link.removeListener(l2);
		String m2 = "alp://spla/" + pin + "\n";
		assertThat(toArduinoWasSent(), is(m1 + m1 + m2));
	}

	@Test(timeout = TIMEOUT)
	public void canReceiveAnalogPinChange() throws IOException {
		final List<AnalogPinValueChangedEvent> analogEvents = new ArrayList<AnalogPinValueChangedEvent>();
		EventListenerAdapter listener = new EventListenerAdapter() {
			@Override
			public void stateChanged(AnalogPinValueChangedEvent event) {
				analogEvents.add(event);
			}
		};
		this.link.addListener(listener);
		int pin = anyPositive(int.class);
		int value = anyPositive(int.class);
		String message = alpProtocolMessage(ANALOG_PIN_READ).forPin(pin)
				.withValue(value);
		simulateArdunoSend(message);
		waitUntilRead(this.bytesRead, message.length() - 1);
		assertThat(analogEvents, eventFor(analogPin(pin)).withValue(value));
	}

	@Test(timeout = TIMEOUT)
	public void doesSendStartListeningDigitalCommangToArduino()
			throws IOException {
		int pin = anyPositive(int.class);
		this.link.addListener(new FilteredEventListenerAdapter(digitalPin(pin),
				null));
		assertThat(toArduinoWasSent(), is("alp://srld/" + pin + "\n"));
	}

	@Test(timeout = TIMEOUT)
	public void canReceiveDigitalPinChange() throws IOException {
		final List<DigitalPinValueChangedEvent> digitalEvents = new ArrayList<DigitalPinValueChangedEvent>();
		EventListenerAdapter listener = new EventListenerAdapter() {
			@Override
			public void stateChanged(DigitalPinValueChangedEvent event) {
				digitalEvents.add(event);
			}
		};
		this.link.addListener(listener);
		int pin = anyPositive(int.class);
		String message = alpProtocolMessage(DIGITAL_PIN_READ).forPin(pin)
				.withState(true);
		simulateArdunoSend(message);
		waitUntilRead(this.bytesRead, message.length() - 1);
		assertThat(digitalEvents, eventFor(digitalPin(pin)).withValue(true));
	}

	@Test(timeout = TIMEOUT)
	public void canFilterPins() throws IOException {
		int pin = anyPositive(int.class);
		final List<DigitalPinValueChangedEvent> digitalEvents = new ArrayList<DigitalPinValueChangedEvent>();
		EventListenerAdapter listener = new EventListenerAdapter() {
			@Override
			public void stateChanged(DigitalPinValueChangedEvent event) {
				digitalEvents.add(event);
			}
		};
		this.link.addListener(new FilteredEventListenerAdapter(
				digitalPin(anyOtherPin(pin)), listener));
		String message = alpProtocolMessage(DIGITAL_PIN_READ).forPin(pin)
				.withState(true);
		simulateArdunoSend(message);
		waitUntilRead(this.bytesRead, message.length() - 1);
		List<DigitalPinValueChangedEvent> emptyList = Collections.emptyList();
		assertThat(digitalEvents, is(emptyList));
	}

	@Test(timeout = TIMEOUT)
	public void canSendKbdEvents() throws IOException {
		this.link.sendKeyPressEvent('#', 1, 2, 3, 4);
		assertThat(toArduinoWasSent(), is("alp://kprs/chr#cod1loc2mod3mex4\n"));
	}

	@Test(timeout = TIMEOUT)
	public void canReadRawMessagesRead() throws IOException {
		String message = alpProtocolMessage(DIGITAL_PIN_READ).forPin(
				anyPositive(int.class)).withState(true);
		final StringBuilder sb = new StringBuilder();
		this.connection.addListener(new ListenerAdapter() {
			@Override
			public void received(byte[] bytes) throws IOException {
				sb.append(new String(bytes));
			}
		});
		simulateArdunoSend(message);
		waitUntilRead(this.bytesRead, message.length());
		assertThat(sb.toString(), is(message));
	}

	@Test(timeout = TIMEOUT)
	public void canReadRawMessagesSent() throws IOException {
		final StringBuilder sb = new StringBuilder();
		this.connection.addListener(new ListenerAdapter() {
			@Override
			public void sent(byte[] bytes) throws IOException {
				sb.append(new String(bytes));
			}
		});
		int pin = anyPositive(int.class);
		int value = anyPositive(int.class);
		this.link.switchAnalogPin(analogPin(pin), value);
		assertThat(sb.toString(), is("alp://ppin/" + pin + "/" + value + "\n"));
	}

	private int anyPositive(Class<? extends Number> numClass) {
		return new Random(System.currentTimeMillis()).nextInt(MAX_VALUE);
	}

	private int anyOtherPin(int pin) {
		return pin + 1;
	}

	private void simulateArdunoSend(String message) throws IOException {
		// this is not performance optimal but better to read than byte[]
		// creation and two system arraycopies
		this.arduinosOutputStream.write((message + new String(AL_PROTO
				.getReadDivider())).getBytes());
	}

	private String toArduinoWasSent() {
		return this.os.toString();
	}

	private static void waitUntilRead(AtomicInteger completed, int toRead) {
		while (completed.get() < toRead) {
			try {
				MILLISECONDS.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
