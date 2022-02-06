package com.minecrafttas.tasmod.networking;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.minecrafttas.tasmod.TASmod;
import com.minecrafttas.tasmod.exceptions.ClientAlreadyRunningException;
import com.minecrafttas.tasmod.networking.packets.TASmodPacket;

import net.minecraft.client.Minecraft;

/**
 * The TASmod itself has a custom connection running next to the minecraft one. 
 * It's necessary since the integrated packet connection is tick-based and therefore cannot communicate inbetween ticks.
 * 
 * IMPLEMENTATION NOTICE:
 * The client creates a separate thread to run off so that it's non-blocking. Therefore it uses a queue for outgoing packets.
 * @author Pancake
 */
public class CustomTASmodClient {

	/**
	 * This is the thread that runs the client. It will exit once the client has disconnected.
	 */
	private static Thread instance;
	
	/**
	 * This is the client socket. Interrupting it will always close the connection and end the thread (instance).
	 */
	private static Socket clientSocket;
	
	/**
	 * This queue of packets is going to be sent by another thread.
	 */
	private static BlockingQueue<TASmodPacket> packetsToSend = new LinkedBlockingQueue<>(); // Initialize with something so this cannot cause a npe
	
	/**
	 * Adds a packet to the queue of packets to send
	 * @param packet Packet to send
	 */
	public static void sendPacket(TASmodPacket packet) {
		if (CustomTASmodClient.instance == null)
			return;
		if (!CustomTASmodClient.instance.isAlive())
			return;
		CustomTASmodClient.packetsToSend.add(packet);
	}
	
	/**
	 * Once the client enters connect phase a separate client thread is created.
	 * 
	 * IMPLEMENTATION NOTICE:
	 * Called from ClientTASmod.
	 * 
	 * @throws IOException Fatal Exception, the socket couldn't be closed
	 * @throws If the last client wasn't succesfully shut down it will throw an exception and forcefully shut down the client
	 */
	public static void createClient() throws ClientAlreadyRunningException, IOException {
		boolean isRunning = CustomTASmodClient.instance == null ? false : CustomTASmodClient.instance.isAlive();
		// Cancel the currently running server
		if (isRunning)
			CustomTASmodClient.clientSocket.close();
		
		// Clear the list of packets to send
		CustomTASmodClient.packetsToSend = new LinkedBlockingQueue<>();
		// Start a client socket
		CustomTASmodClient.instance = new Thread(() -> {
			// Find the address of the server we are currently connected to (in minecraft's connection)
			String serverIp = Minecraft.getMinecraft().isIntegratedServerRunning() ? "127.0.0.1" : Minecraft.getMinecraft().getCurrentServerData().serverIP;
			// Connect to the server
			try(Socket clientSocket = new Socket(serverIp, 3111)) {
				CustomTASmodClient.clientSocket = clientSocket;
				// Handle the socket
				CustomTASmodCommon.handleSocket(clientSocket, CustomTASmodClient.packetsToSend); // this will create a new thread for outstream and use the current thread for instream
			} catch (EOFException  | SocketException | InterruptedIOException exception) {
				// The custom TASmod client was closed and the end of stream was reached. The socket was shut down properly.
				TASmod.LOGGER.debug("Custom TASmod client was shutdown");
			} catch (Exception exception) {
				TASmod.LOGGER.error("Custom TASmod client was unexpectedly shutdown {}", exception);
			}
		});
		CustomTASmodClient.instance.setDaemon(true); // If daemon is set, the jvm will quit without waiting for this thread to finish
		CustomTASmodClient.instance.start();
		
		// Make sure to throw an exception if the server was running
		if (isRunning)
			throw new ClientAlreadyRunningException();
	}

	/**
	 * Kills the custom TASmod client if is running
	 * @throws IOException Thrown if the socket couldn't be closed
	 */
	public static void killClient() throws IOException {
		if (CustomTASmodClient.instance != null)
			CustomTASmodClient.clientSocket.close();
	}
	
}
