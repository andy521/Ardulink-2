/**
Copyright 2013 project Ardulink http://www.ardulink.org/
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.ardulink.rest;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.port;
import static org.ardulink.core.Pin.analogPin;
import static org.ardulink.core.Pin.digitalPin;
import static org.ardulink.core.events.DefaultAnalogPinValueChangedEvent.analogPinValueChanged;
import static org.ardulink.core.events.DefaultDigitalPinValueChangedEvent.digitalPinValueChanged;
import static org.ardulink.rest.RestRouteBuilder.VAR_PORT;
import static org.ardulink.rest.RestRouteBuilder.VAR_TARGET;
import static org.ardulink.testsupport.mock.StaticRegisterLinkFactory.ardulinkUri;
import static org.ardulink.testsupport.mock.StaticRegisterLinkFactory.register;
import static org.ardulink.testsupport.mock.TestSupport.getMock;
import static org.ardulink.util.MapBuilder.newMapBuilder;
import static org.ardulink.util.ServerSockets.freePort;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.ardulink.core.AbstractListenerLink;
import org.ardulink.core.Link;
import org.ardulink.core.Pin;
import org.ardulink.core.Pin.AnalogPin;
import org.ardulink.core.Pin.DigitalPin;
import org.ardulink.core.Tone;
import org.ardulink.core.convenience.Links;
import org.ardulink.core.events.AnalogPinValueChangedEvent;
import org.ardulink.core.events.DigitalPinValueChangedEvent;
import org.ardulink.core.events.EventListener;
import org.ardulink.core.events.PinValueChangedEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * [ardulinktitle] [ardulinkversion]
 * 
 * project Ardulink http://www.ardulink.org/
 * 
 * [adsense]
 *
 */
public class ArdulinkRestTest {

	@Before
	public void setup() {
		port = freePort();
	}

	@Test
	public void canSwitchDigitalPin() throws Exception {
		try (Link link = Links.getLink("ardulink://mock")) {
			Link mock = getMock(link);
			try (CamelContext context = startCamelRest("ardulink://mock")) {
				int pin = 5;
				boolean state = true;
				given().body(state).post("/pin/digital/{pin}", pin).then().statusCode(200);
				verify(mock).switchDigitalPin(digitalPin(pin), state);
				context.stop();
			}
			verify(mock).close();
		}
	}

	@Test
	public void canSwitchAnalogPin() throws Exception {
		try (Link link = Links.getLink("ardulink://mock")) {
			Link mock = getMock(link);
			try (CamelContext context = startCamelRest("ardulink://mock")) {
				int pin = 9;
				int value = 123;
				given().body(value).post("/pin/analog/{pin}", pin).then().statusCode(200);
				verify(mock).switchAnalogPin(analogPin(pin), value);
				context.stop();
			}
			verify(mock).close();
		}
	}

	@Test
	public void canReadDigitalPin() throws Exception {
		int pin = 5;
		boolean state = true;
		try (AbstractListenerLink link = createAbstractListenerLink(digitalPinValueChanged(digitalPin(pin), state))) {
			try (CamelContext context = startCamelRest(ardulinkUri(register(link)))) {
				given().get("/pin/digital/{pin}", pin).then().statusCode(200).body(is(String.valueOf(state)));
				context.stop();
			}
		}
	}

	@Test
	public void canReadAnalogPin() throws Exception {
		int pin = 7;
		int value = 456;
		try (AbstractListenerLink link = createAbstractListenerLink(analogPinValueChanged(analogPin(pin), value))) {
			try (CamelContext context = startCamelRest(ardulinkUri(register(link)))) {
				given().get("/pin/analog/{pin}", pin).then().statusCode(200).body(is(String.valueOf(value)));
				context.stop();
			}
		}
	}

	@Test
	public void canEnableAndDisableListeningDigitalPin() throws Exception {
		int pin = 5;
		try (Link link = Links.getLink("ardulink://mock")) {
			Link mock = getMock(link);
			try (CamelContext context = startCamelRest("ardulink://mock")) {
				given().body("listen=true").patch("/pin/digital/{pin}", pin).then().statusCode(200);
				verify(mock).startListening(digitalPin(pin));
				given().body("listen=false").patch("/pin/digital/{pin}", pin).then().statusCode(200);
				verify(mock).stopListening(digitalPin(pin));
				context.stop();
			}
			verify(mock).close();
		}

	}

	@Test
	public void canEnableAndDisableListeningAnalogPin() throws Exception {
		int pin = 7;
		try (Link link = Links.getLink("ardulink://mock")) {
			Link mock = getMock(link);
			try (CamelContext context = startCamelRest("ardulink://mock")) {
				given().body("listen=true").patch("/pin/analog/{pin}", pin).then().statusCode(200);
				verify(mock).startListening(analogPin(pin));
				given().body("listen=false").patch("/pin/analog/{pin}", pin).then().statusCode(200);
				verify(mock).stopListening(analogPin(pin));
				context.stop();
			}
			verify(mock).close();
		}

	}

	private CamelContext startCamelRest(String target) throws Exception {
		CamelContext context = new DefaultCamelContext();
		context.getPropertiesComponent()
				.setInitialProperties(newMapBuilder().put(VAR_TARGET, target).put(VAR_PORT, port).asProperties());
		context.addRoutes(new RestRouteBuilder());
		context.start();
		return context;
	}

	private AbstractListenerLink createAbstractListenerLink(PinValueChangedEvent... events) {
		return new AbstractListenerLink() {

			@Override
			public Link addListener(EventListener listener) throws IOException {
				Link link = super.addListener(listener);
				for (PinValueChangedEvent event : events) {
					if (event instanceof AnalogPinValueChangedEvent) {
						fireStateChanged((AnalogPinValueChangedEvent) event);
					} else if (event instanceof DigitalPinValueChangedEvent) {
						fireStateChanged((DigitalPinValueChangedEvent) event);
					}
				}
				return link;
			}

			@Override
			public long switchDigitalPin(DigitalPin digitalPin, boolean value) throws IOException {
				return 0;
			}

			@Override
			public long switchAnalogPin(AnalogPin analogPin, int value) throws IOException {
				return 0;
			}

			@Override
			public long stopListening(Pin pin) throws IOException {
				return 0;
			}

			@Override
			public long startListening(Pin pin) throws IOException {
				return 0;
			}

			@Override
			public long sendTone(Tone tone) throws IOException {
				return 0;
			}

			@Override
			public long sendNoTone(AnalogPin analogPin) throws IOException {
				return 0;
			}

			@Override
			public long sendKeyPressEvent(char keychar, int keycode, int keylocation, int keymodifiers,
					int keymodifiersex) throws IOException {
				return 0;
			}

			@Override
			public long sendCustomMessage(String... messages) throws IOException {
				return 0;
			}
		};
	}
}
