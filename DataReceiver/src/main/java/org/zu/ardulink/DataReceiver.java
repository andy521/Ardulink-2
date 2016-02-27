/**
Copyright 2013 Luciano Zu project Ardulink http://www.ardulink.org/

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

package org.zu.ardulink;

import static com.github.pfichtner.ardulink.core.Pin.analogPin;
import static com.github.pfichtner.ardulink.core.Pin.digitalPin;
import static com.github.pfichtner.ardulink.core.convenience.Links.setChoiceValues;
import static java.lang.String.format;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.pfichtner.ardulink.core.Connection;
import com.github.pfichtner.ardulink.core.ConnectionBasedLink;
import com.github.pfichtner.ardulink.core.Link;
import com.github.pfichtner.ardulink.core.events.AnalogPinValueChangedEvent;
import com.github.pfichtner.ardulink.core.events.DigitalPinValueChangedEvent;
import com.github.pfichtner.ardulink.core.events.EventListener;
import com.github.pfichtner.ardulink.core.linkmanager.LinkManager;

/**
 * [ardulinktitle] [ardulinkversion]
 * 
 * @author Luciano Zu project Ardulink http://www.ardulink.org/
 * @author Peter Fichtner
 * 
 *         [adsense]
 */
public class DataReceiver {

	@Option(name = "-delay", usage = "Do a n seconds delay after connecting")
	private int sleepSecs = 10;

	@Option(name = "-v", usage = "Be verbose")
	private boolean verbose;

	@Option(name = "-connection", usage = "Connection URI to the arduino")
	private String connString = "ardulink://serial";

	@Option(name = "-d", aliases = "--digital", usage = "Digital pins to listen to")
	private int[] digitals = new int[] { 2 };

	@Option(name = "-a", aliases = "--analog", usage = "Analog pins to listen to")
	private int[] analogs = new int[0];

	@Option(name = "-msga", aliases = "--analogMessage", usage = "Message format for analog pins")
	private String msgAnalog = "PIN state changed. Analog PIN: %s Value: %s";

	@Option(name = "-msgd", aliases = "--digitalMessage", usage = "Message format for digital pins")
	private String msgDigital = "PIN state changed. Digital PIN: %s Value: %s";

	private Link link;

	private static final Logger logger = LoggerFactory
			.getLogger(DataReceiver.class);

	public static void main(String[] args) throws URISyntaxException, Exception {
		new DataReceiver().doMain(args);
	}

	private void doMain(String[] args) throws URISyntaxException, Exception {
		CmdLineParser cmdLineParser = new CmdLineParser(this);
		try {
			cmdLineParser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			cmdLineParser.printUsage(System.err);
			return;
		}
		work();
	}

	private void work() throws URISyntaxException, Exception {
		this.link = createLink();

		try {
			logger.info("Wait a while for Arduino boot");
			TimeUnit.SECONDS.sleep(sleepSecs);
			logger.info("Ok, now it should be ready...");

			link.addListener(eventListener());

			for (int analog : analogs) {
				link.startListening(analogPin(analog));
			}
			for (int digital : digitals) {
				link.startListening(digitalPin(digital));
			}

			if (verbose && link instanceof ConnectionBasedLink) {
				((ConnectionBasedLink) link).getConnection().addListener(
						rawDataListener());
			}

		} catch (InterruptedException e1) {
			throw new RuntimeException(e1);
		}

	}

	private EventListener eventListener() {
		return new EventListener() {
			@Override
			public void stateChanged(DigitalPinValueChangedEvent event) {
				logger.info(format(msgDigital, event.getPin(), event.getValue()));
			}

			@Override
			public void stateChanged(AnalogPinValueChangedEvent event) {
				logger.info(format(msgAnalog, event.getPin(), event.getValue()));
			}
		};
	}

	private Connection.Listener rawDataListener() {
		return new Connection.ListenerAdapter() {
			@Override
			public void received(byte[] bytes) {
				logger.info("Message from Arduino: %s", new String(bytes));
			}
		};
	}

	private Link createLink() throws Exception, URISyntaxException {
		return setChoiceValues(
				LinkManager.getInstance().getConfigurer(new URI(connString)))
				.newLink();
	}

}