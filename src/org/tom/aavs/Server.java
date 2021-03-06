package org.tom.aavs;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import netP5.NetAddress;
import oscP5.OscMessage;
import oscP5.OscP5;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.video.Movie;

public class Server extends PApplet {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4547488521500559579L;

	public static final boolean FULLSCREEN = false;

	float viewX = 520;
	float viewY = 0;
	float viewHeight = 320;
	float viewWidth = 426;
	float viewSeparation = 10;

	int totalClients = 4;
	int activeClient = 0;
	int lastActiveClient = 0;

	private int serverPort = 11300;
	private int clientPort = 11200;
	private int clientDatagramPort = 11100;
	
	private boolean clientChanged = true;

	OscP5[] oscP5;
	String[] clients;
	Frame[] trackedFrames;

	NetAddress[] clientAddresses;
	OscMessage activeMessage, videoPlayMessage;

	boolean[] receivedTrackingClient;

	DatagramSocket ds;  // to stream video

	private PVector[] kinects;

	float stageSide = 10f;
	MIDI [] midiBackground;
	MIDI midiRain;

	MIDI test;

	private boolean transmittingFrames = true;
	private boolean transmittingCommands = false;

	PVector frameCoordinates = new PVector(-1000, -1000);

	Movie currentVideo;
	String currentFilename;

	int MIDI_CHANNELS[] = {1, 2, 3, 4, 5}; // tom's computer

	PVector[][] videoAreas; // an array of two pvectors are our areas
	private int totalAreas = 4;

	int lastChangeTimestamp;
	
	boolean debug = false;

	float videoVolume;
	private int lastClientMessage;

	public void setup() {
		size (1400, 800, P3D);		
		frameRate(30);
	
		videoVolume = 0.3f;
		println("AAVS server");
		println("sketchpath: " + sketchPath);
		println("datapath: " + dataPath(""));

		lastChangeTimestamp = -1;
		currentFilename = "";

		videoAreas = new PVector[totalAreas][2];

		// first area
		videoAreas [0][0] = new PVector (0, 0); // start point
		videoAreas [0][1] = new PVector (50, 50); // end point;

		// second area
		videoAreas [1][0] = new PVector (100, 100); // start pointX
		videoAreas [1][1] = new PVector (150, 150); // end point;

		// third area
		videoAreas [2][0] = new PVector (70, 10); // start point
		videoAreas [2][1] = new PVector (100, 40); // end point;

		// fourth area
		videoAreas [3][0] = new PVector (10, 70); // start point
		videoAreas [3][1] = new PVector (90, 160); // end point;

		trackedFrames = new Frame[totalClients];
		receivedTrackingClient = new boolean[totalClients];
		clients = new String[totalClients];
		clientAddresses = new NetAddress[totalClients];
		kinects = new PVector[totalClients];

		for (int i = 0; i < totalClients; i++) kinects[i] = new PVector();

		kinects[0].x = 0;
		kinects[0].y = 0;

		kinects[1].x = stageSide / 2;
		kinects[1].y = stageSide / 2;

		kinects[2].x = stageSide;
		kinects[2].y = 0;

		kinects[3].x = stageSide / 2;
		kinects[3].y = -stageSide / 2;


		// todo get this info from txt file
		for (int i = 0; i < totalClients; i++) {
			//clientAddresses[i] = new NetAddress("127.0.0" + (i+1), clientPort);   // 192.168.0."  TODO load from config file
			clientAddresses[i] = new NetAddress("192.168.0" + (i+1), clientPort);   // 192.168.0."  
			clients[i] = "192.168.0." + (i+1); // clients we are writing to

			//trackedFrames[i] = new Frame (-100, -100, -100, -100, -100, -100, -100, -100);
			trackedFrames[i] = new Frame (200, 100, 100, 200, 200, 200, 100, 100);					

			/*
					 (int)random (640), (int)random (480),					 
					(int)random (640), (int)random (480),
					(int)random (640), (int)random (480),
					(int)random (640), (int)random (480)
					);
			 */

			//200, 100, 100, 200, 200, 200);

			receivedTrackingClient[i] = false;
		}
		
		oscP5 = new OscP5[totalClients];
		for (int i = 0; i < totalClients; i++){
			oscP5[i] = new OscP5(this, serverPort+i); // port we are listening to	
		}
		
		try {
			ds = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		activeMessage = new OscMessage("/active");
		videoPlayMessage = new OscMessage("/play");

		midiBackground = new MIDI[totalClients];
		for (int i = 0; i < totalClients; i ++) {
			midiBackground[i] = new MIDI(this, MIDI_CHANNELS[i]);		 // second parameter is the device number
		}
		currentVideo = null;	

		test = new MIDI(this, 0);
		midiRain = new MIDI (this, 5);
		midiRain.note(60, 128); // rain is always on.		
	}

	public void movieEvent(Movie m) {
		m.read();
	}

	public void sendImage (PImage img, String ip, int port) {
		// We need a buffered image to do the JPG encoding

		if (img != null) {
			if (img.width != 0 && img.height != 0){
				BufferedImage bimg = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB);

				// Transfer pixels from localFrame to the BufferedImage
				img.loadPixels();
				bimg.setRGB(0, 0, img.width, img.height, img.pixels, 0, img.width);

				// Need these output streams to get image as bytes for UDP communication
				ByteArrayOutputStream baStream = new ByteArrayOutputStream();
				BufferedOutputStream bos = new BufferedOutputStream(baStream);

				// compress the BufferedImage into a JPG and put it in the BufferedOutputStream
				try {
					ImageIO.write(bimg, "jpg", bos);
				} 
				catch (IOException e) {
					e.printStackTrace();
				}

				// Get the byte array, which we will send out via UDP!
				byte[] packet = baStream.toByteArray();

				// Send JPEG data as a datagram
				// println("Sending datagram with " + packet.length + " bytes");

				// we send the frame to the active client

				//		System.out.println(packet.length);
				try {			
					ds.send(new DatagramPacket(packet, packet.length, InetAddress.getByName(ip), port));
				} 
				catch (Exception e) {
					e.printStackTrace();
				}	
			}
		}
	}

	private String getVideoFilename (PVector coords) {

		int TOTAL_FILES_PER_PROJECTOR = 4;
		int PROBABILTY_CHANGE = 5;
		int MAX_TIME_VIDEO = 40000;

		int now = millis();
		
		if ( (now - lastChangeTimestamp) > MAX_TIME_VIDEO ||   
			random(1000) > (1000-PROBABILTY_CHANGE) ) { // 	|| 
				//clientChanged) {

			lastChangeTimestamp = now;
			return "projector" + activeClient + (int)random(TOTAL_FILES_PER_PROJECTOR) + ".mov";								
		}


		return currentFilename;
	}

	public PImage getVideoFrame (PVector coords) {
		PImage img;
		// img = loadImage("car.jpg");

		// TODO here we should have two modes of reproduction: frame-by-frame (no sound) and looped
		// as of now we only have looped
		
		String newFilename = getVideoFilename(coords); 

		if (!currentFilename.equals(newFilename)) {
			
			if (debug) System.out.println("new video");
			if (currentVideo != null){				
				currentVideo.dispose();
			}
			
			currentFilename = newFilename;
			currentVideo = new Movie (this, currentFilename); 	
			currentVideo.volume(videoVolume);
			currentVideo.loop();

			if (transmittingCommands) {
				sendPlayCommand();
			}
		}

		img = currentVideo;					
		return img;
	}


	public void draw() {
		background(0);
		stroke(255);
		strokeWeight(2);
		fill(0);

		// 4 "views"
		if (activeClient == 0) {
			stroke (255, 0, 0);			
		} else {
			stroke (255, 255, 255);
		}		
		rect(viewX + viewSeparation, viewY + viewSeparation, viewWidth, viewHeight);
		if (activeClient == 1) {
			stroke (255, 0, 0);			
		} else {
			stroke (255, 255, 255);
		}
		rect(viewX + viewWidth + viewSeparation * 2, viewY + viewSeparation, viewWidth, viewHeight);
		if (activeClient == 2) {
			stroke (255, 0, 0);			
		} else {
			stroke (255, 255, 255);
		}
		rect(viewX + viewSeparation, viewY + viewHeight + viewSeparation * 2, viewWidth, viewHeight);
		if (activeClient == 3) {
			stroke (255, 0, 0);			
		} else {
			stroke (255, 255, 255);
		}
		rect(viewX + viewWidth + viewSeparation * 2, viewY + viewHeight + viewSeparation * 2, viewWidth, viewHeight);
		stroke (255, 255, 255);
		rect(viewSeparation, viewSeparation, 510, 510);

		frameCoordinates.x = 0;
		frameCoordinates.y = 0;


		//	if (receivedMessagesFromAllClients()) { //TODO test the deletion of received all Messages

		/* 	
		 * need to do the following:

			  		locate frame in 2D space
					decide which module is the active module
					getFrameToProject (currentState, location3D)
					send active/which message to all modules
					send JPEG frame to active module					

			 		to locate frame in 2D space

			 			find the frame with the biggest area (active frame)
						find the secondary frame // 
						distance (depending on which frame active = x or y)
						distance to center = y or x

						also draw the location on the location part of the interface

		 */

		// identifying active module
		int active = 0;
		float maxArea = trackedFrames[0].getArea();
		for (int i = 1; i < totalClients; i++) {
			if (trackedFrames[i].getArea() > maxArea) {
				maxArea = trackedFrames[i].getArea();
				active = i;
			}
		}

		if (trackedFrames[lastClientMessage].getTrackedVerticesSize() == 4) {
			active = lastClientMessage;
		}
		
		
		// identifying secondary module
		int side = (active + 1) % 4;
		int oppositeSide = (side + 2) % 4;
		if (trackedFrames[side].getTrackedVerticesSize() < trackedFrames[oppositeSide].getTrackedVerticesSize()) {
			side = oppositeSide;	
		}

		// we assign the centroids to the frameCoordinates PVector to use as selector of videos later
		if (active % 2 == 0) {  
			frameCoordinates.x = trackedFrames[active].centroid().x;
			frameCoordinates.y = trackedFrames[side].centroid().y;			
		} else {			
			frameCoordinates.x = trackedFrames[side].centroid().x;
			frameCoordinates.y = trackedFrames[active].centroid().y;
		}

		activeClient = active;
		
		fill (255, 0, 0);


		// we draw the content of the virtual frame. This is not up to scale, but //TODO needs to be scaled correctly
		pushMatrix();
		translate(viewSeparation, viewSeparation);

		ellipse (frameCoordinates.x, frameCoordinates.y, 10, 10);

		// 
		noFill();
		stroke(200, 200, 200);

		
	/// unused as of now
		for (int i = 0; i < totalAreas; i++) {
			rect(videoAreas[i][0].x,videoAreas[i][0].y, videoAreas[i][1].x, videoAreas[i][1].y);
		}

		popMatrix();



		//} // if received from all clients	


		// we get the video frame we will stream
		PImage img = getVideoFrame (frameCoordinates);

		// we tell the clients which one is active
		activateClient(activeClient);

		// we stream the frame
		if (transmittingFrames) {
			sendImage(img, clients[activeClient], clientDatagramPort);
		}

		drawStatus();


		// we draw the frames on our own display
		trackedFrames[0].draw((PApplet)this, viewX + viewSeparation, viewY + viewSeparation, 0.66f, img);
		trackedFrames[1].draw((PApplet)this, viewX + viewSeparation * 2 + viewWidth, viewY + viewSeparation, 0.66f, img);
		trackedFrames[2].draw((PApplet)this, viewX + viewSeparation, viewY + viewHeight + viewSeparation * 2, 0.66f, img);
		trackedFrames[3].draw((PApplet)this, viewX + viewSeparation * 2 + viewWidth, viewY + viewHeight + viewSeparation * 2, 0.66f, img);


	}

	private void sendPlayCommand() {
		videoPlayMessage.clearArguments();
		videoPlayMessage.add(currentFilename);
		oscP5[0].send(videoPlayMessage, clientAddresses[activeClient]);		

	}

	private void activateClient(int which) {

		// fixme! (we only have one client in testing)  
		if (which != lastActiveClient) { 

			clientChanged = true;

			for (int i = 0; i < totalClients; i++) {
				activeMessage.clearArguments();

				int[] params = new int[1];
				if (i == which) {
					params[0] = 1;
				}
				else {
					params[0] = 0;
				}

				activeMessage.add(params); 			

				oscP5[i].send(activeMessage, clientAddresses[i]);				
			}

			// midiBackground[lastActiveClient].note(60,  0); // silence the last active
			
			for (int i = 0; i < totalClients; i++) {
				if (i != which) {
					midiBackground[i].note(60,  0); // silence  all except the active
				}
			}
			
			midiBackground[which].note(60, 127); // start the new one

			lastActiveClient = which;
		} else {
			clientChanged = false;
		}


	}

	private boolean receivedMessagesFromAllClients() {

		boolean all = true; 

		for (int i = 0; i < totalClients && !all; i++) {
			all = all && receivedTrackingClient[i];
		}

		return all;
	}

	private void drawStatus() {
		textSize(14);
		if (debug) text ("debug mode enabled", 50, 600);
		text ("active client: " + activeClient, 50, 650);
		text("current video: " + currentFilename, 50, 700);
		text("current video volume: " + videoVolume, 50, 750);
		fill(255);
	}

	void oscEvent(OscMessage msg) {

		// println("server: received " + msg.addrPattern() + ", typetad: " + msg.typetag() + ", from: " + msg.address());

		String adr = msg.address();
		String[] adrBytes = split (adr, '.');
		
		int clientNumber = new Integer(adrBytes [3]).intValue() - 1; // 192.168.0.1 -> client 0
		if (debug) {
			System.out.println("received a message from client (0..3): " + clientNumber);
		}
		
		lastClientMessage = clientNumber;

		// println("server: client number (last ip byte): " + clientNumber);

		receivedTrackingClient[clientNumber] = true;

		int totalVertices =  msg.typetag ().length() / 2; // 2 coordiantes per vertex, 
		// and we are only sending a list of integers with the coordinates

		trackedFrames[clientNumber].v = new ArrayList<PVector>();

		for (int i = 0; i < totalVertices; i++) {
			PVector vertex = new PVector(((Integer) msg.arguments()[i*2]).intValue(), 
					((Integer) msg.arguments()[i*2+1]).intValue());

			trackedFrames[clientNumber].v.add(vertex);
		}
	}

	
	private void reset() {
		for (int i = 0; i < totalClients; i++) {
			receivedTrackingClient[i] = false;
		}
	}

	public void keyPressed() {
		switch (key) {
		
		case 'V':
			videoVolume += 0.01f;
			if (videoVolume > 1f) videoVolume = 1f;
			if (debug) System.out.println("volume: " + videoVolume);
			if (currentVideo != null) {
				currentVideo.volume(videoVolume);
			}
			break;
			
		case 'v':
			videoVolume -= 0.01f;
			if (videoVolume < 0) videoVolume = 0;
			if (debug) System.out.println("volume: " + videoVolume);
			if (currentVideo != null) {
				currentVideo.volume(videoVolume);
			}
			break;
			
			
		case 'p':
			for (int i = 0; i < totalClients; i++) {
				trackedFrames[i] = new Frame (						
						(int)random (640), (int)random (480),
						(int)random (640), (int)random (480),
						(int)random (640), (int)random (480),
						(int)random (640), (int)random (480)
						);
			}
			break;

		case '1': case '2': case '3': case '4':			
			activeClient = keyCode-49;			
			backgroundSound (activeClient);
			break;

		case 'a':

			test.note(0,  127);


			break;
			
		case ' ':
			debug = !debug;
			break;

		case 's':

			for (int i = 0; i < totalClients; i++) {
				midiBackground[i].note(60, 128); 
			}
			break;

		case 'm':
			sendPlayCommand();
			break;
		}
	}

	public void backgroundSound(int activeClient) {

	}

	static public void main(String args[]) { 
		if (FULLSCREEN) {
			PApplet.main(new String[] { "--present", "--bgcolor=#000000", "--hide-stop", "--present-stop-color=#000000", "org.tom.aavs.Server" });
		} else {
			PApplet.main(new String[] { "--bgcolor=#000000", "--hide-stop", "--present-stop-color=#000000", "org.tom.aavs.Server" });
		}

	}
}