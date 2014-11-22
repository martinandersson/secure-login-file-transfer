# Secure login and authenticated file transfers
This is a proof of concept Java application that demonstrates:

 - Secure Remote Protocol ([SRP])
 - [AES]/[GCM] encryption
 - Binary file transfer over [WebSocket]

This application is able to login/authenticate a user without providing the server a password, and then transmit files securely using an insecure channel. Both ends will compute a fresh symmetric key used for the session only and the server need not publish a public key or use a third party certificate authority.

The application uses [Nimbus] as the SRP provider, and the default SunJCE provider for all cryptographic tasks.

### Front end client
The human user (you) launch a rich client built using JavaFX and may dynamically select which file to transfer, whether or not the file transfer shall be chunked, encrypted, or even if the sent bytes should be manipulated to test GCM:s ability to authenticate messages. The GUI will report time to transfer the file and the time server needed for decryption.

![File transfer in progress][Screen1]

### Back end
The server code is written using Java EE 7 and has been tested to work on GlassFish 4.1. WildFly 8.1.0 has some strange bug that makes it unable to properly use websocket message handlers registered during runtime (TODO: report).

Using the GUI, one may also during runtime select which receiving strategy the server should use to receive the bytes. Maybe the server should use a `InputStream`, maybe he should use a `byte[]`? You decide! All possible ways that a [`MessageHandler`](https://docs.oracle.com/javaee/7/api/javax/websocket/MessageHandler.html) can be used in to received binary data is provided.

### Installation
Clone this repository, which is a Maven project. Optionally change the server's [path](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/MyWebSocket.java#L97) where he store files (the directory must exist). Build, deploy the war to server and run the client jar like so:
```cmd
java -jar client-1.0.0-SNAPSHOT.jar
```
..or not using a console attached:
```cmd
javaw -jar client-1.0.0-SNAPSHOT.jar
```
However, it is most recommended that you do keep the console open as the client (and server) log many important and interesting things such as salt, computed key, messages sent and received et cetera (this application is for testing purposes only!). I usually run the client and deploy the server war file using NetBeans IDE which is working flawlessly. With that said, I haven't tested any other configuration. If you use another IDE and find problems with the build, let me know or contribute okay =)

Make sure you're using the latest [JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).

### How it works
The protocol by which the client and server adhere to is described in the JavaDoc of [`MyWebSocket`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/MyWebSocket.java).

### Connecting with the server
Once you launch the client, you'll see a screen asking you to connect with the server.

![Connect to server][Screen2]

As stated previously somewhere, WildFly 8.1.0 currently doesn't work. You can deploy to WildFly, connect to WildFly and authenticate the user with WildFly. It is when sending files to WildFly that things go to hell (TODO: report).

The connect button will lit up once you have chosen a URL the client should use.

 - **Client** related classes in focus
  - [`Page1Controller`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/pages/Page1Controller.java)
  - [`ServerConnection`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/ServerConnection.java) (also has a comment describing the use of `volatile` in double-checked locking!)

 - **Server** related classes in focus
  - [`MyWebSocket`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/MyWebSocket.java)

### User registration and authentication
After the client connect, a registration/login screen will appear.

![Registering and authenticating a user][Screen3]

First step is to register the user. Anything goes in the first two boxes, so make it simple =) After having entered useful data into the first pair of username and password boxes, click the Register button. The client will compute a SRP verifier, send that together with a salt and username to the server who register the user.

Next, enter the same stuff into the next pair of boxes and click on Authenticate. You could experiment here and enter a wrong password and see what happens. Authenticating the user is the same as logging in.

Note that during registration, the user credentials are sent in a insecure manner. Only the authentication part uses SRP to provide a [zero-knowledge password proof](http://en.wikipedia.org/wiki/Secure_Remote_Password_protocol) to the server. A real world application must securely convey the user credentials during registration.

 - **Client** related classes in focus
  - [`Page2Controller`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/pages/Page2Controller.java)
  - [`ClientProcedures`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/login/ClientProcedures.java)
  - [`Authenticate`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/login/Authenticate.java)

 - **Server** related classes in focus
  - [`Credentials`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/login/Credentials.java)
  - [`SRP6ServerLogin`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/login/SRP6ServerLogin.java)

### Sending files
Once you have authenticated yourself, you may begin to send files.

![Send files][Screen4]

Here, you have an abundance of fun stuff to play around with. Next, I'll walk you through each option from top to bottom.

 - **Library** related classes in focus
  - [`AesGcmCipher`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/AesGcmCipher.java)
  - [`ServerStrategy`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/ServerStrategy.java)

 - **Client** related classes in focus
  - [`Page3Controller`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/pages/Page3Controller.java)
  - [`FileSender`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Client/src/main/java/martinandersson/com/client/FileSender.java)

 - **Server** related classes in focus
  - All in package [`martinandersson.com.server.filereceiver`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/tree/master/Server/src/main/java/martinandersson/com/server/filereceiver)

##### Select a file
Browsing for a file should be a no brainer. Be sure to select a file that is not in the [directory](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/MyWebSocket.java#L97) where the server want to save the same file. Doing so will unleash the devil.

Sending really large files can be troublesome and depends on whether or not encryption is enabled, and it depends on the chosen strategy for receiving the file on the server. I haven't experimented with the software myself so much that I can say exactly how each factor play out. Sending large files using encryption and the default SunJCE provider which this software use, **is** problematic due to [internal buffering](http://stackoverflow.com/q/26920906/1268003).

Therefore, *the file may be sent in chunks*. Simply tick the "Send in chunks" radiobutton and select a chunk size. This will make the client send the file in chunks. The server will save each chunks to a temporary file in his ordinary save folder, and once all chunks has been transferred, the chunks are merged into one file and deleted.

It is expected that an encrypted and chunked file transfer is faster than sending an encrypted file in one piece. However, my experience has shown me that it is *dramatically much faster* and that one gain a huge amount of speed even when chunked file transfer is enabled to send unencrypted files.

Please have proper tooling in place to monitor CPU, memory and disk activity. If you send the same file more than just once, you might discover that the OS can cache even large files; making a time comparison between the two file transfers not that reliable. As a best practice to avoid disk caches, make copies of your file and use them interchangeably.

##### Configuration
Tick "Enable encryption (AES/GCM)" to **enable encryption**. Otherwise, the file will be sent unencrypted. You'll notice that sending files unencypted is much much faster than using encryption.

If you enable encryption, then you have one more option. You may **tell the server about it**. Of course, you should tell the server. Otherwise the server will not decrypt the file but save the bytes as is. It could be fun to send an encrypted text document to the server and see the ciphertext in your favorite text editor. But please note that if you don't tell the server, the endpoints will become out of sync and all files sent thereafter will be rejected, unable to authenticate. Reason is that for each file or chunk transfer, the client and the server [reinitialize](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/AesGcmCipher.java#L124) their Cipher instance with a new [initialization vector](http://en.wikipedia.org/wiki/Initialization_vector) (IV) that depends on a message counter. Hence, the two endpoints will become unsynchronized. All future GCM [authentication tags](http://en.wikipedia.org/wiki/Message_authentication_code) sent by the client and the authentication tags computed by the server will not be the same making the server reject all future transfers. If you experiment by sending a file encrypted without telling the server, then you must restart the client afterwards and thereby use a new session.

If you enable encryption, and tell the server about it, then you have a third option: **manipulate a bit in the middle of the stream**. This is a feature you may use to test the authentication part of GCM. The "manipulation" is effectively [man-in-the-middle attack](http://en.wikipedia.org/wiki/Man-in-the-middle_attack). If you chose to manipulate a bit in the middle of the stream, you'll notice that halfway through the file transfer, the client will change just one single bit of all the bits sent to the server and then print a log message (it is always the least significant bit that is flipped):

```
INFO: Manipulated byte 01100000 to 01100001
```

This must have the outcome of failing authentication:

![Send files][Screen5]

Here is a cheatsheet for all available strategies the server may use to receive binary data. Click on the link in the left column to read a strategy description, click on the link in the right column to read some code.

| Strategy | Implementing class | Description |
| -------- | ------------------ | ----------- |
| [Copy InputStream] | [`CopyInputStreamFileReceiver`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/filereceiver/CopyInputStreamFileReceiver.java) | Server will use a `MessageHandler.Whole<InputStream>` that use Files.copy() to transfer all bytes to disk.
| [No-use InputStream] | [`NoUseInputFileReceiver`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/filereceiver/NoUseInputFileReceiver.java) | Server will use a `MessageHandler.Whole<InputStream>` that throw away all bytes without using disk IO.
| [Single-byte InputStream] | [`SingleByteInputStreamFileReceiver`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/filereceiver/SingleByteInputStreamFileReceiver.java) | Server will use a `MessageHandler.Whole<InputStream>` that save the bytes using a buffered FileOutputStream.
| [byte array] | [`ByteArrayFileReceiver`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/filereceiver/ByteArrayFileReceiver.java) | Server will use a `MessageHandler.Partial<byte[]>` that save the bytes using a buffered OutputStream.
| [ByteBuffer] | [`ByteBufferFileReceiver`](https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Server/src/main/java/martinandersson/com/server/filereceiver/ByteBufferFileReceiver.java) | Server will use a `MessageHandler.Partial<ByteBuffer>` that save the bytes using a blocking FileChannel.

### More information

Included in this repository is a document with some of my research ([pdf]/[docx]). Also, be sure to read the source code files where I try my best to add elaborative JavaDoc and source code comments.

If you have a question, feel free to drop me an email: <webmaster@martinandersson.com>.

### License
[MIT](http://choosealicense.com/licenses/mit/)

[SRP]:http://en.wikipedia.org/wiki/Secure_Remote_Password_protocol
[AES]:http://en.wikipedia.org/wiki/Advanced_Encryption_Standard
[GCM]:http://en.wikipedia.org/wiki/Galois/Counter_Mode
[WebSocket]:http://en.wikipedia.org/wiki/WebSocket
[Screen1]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/Client/screenshots/screen1.png
[Screen2]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/Client/screenshots/screen2.png
[Screen3]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/Client/screenshots/screen3.png
[Screen4]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/Client/screenshots/screen4.png
[Screen5]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/Client/screenshots/screen5.png
[Nimbus]:http://connect2id.com/products/nimbus-srp
[docx]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/My%20GCM%20Research.docx
[pdf]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/raw/master/My%20GCM%20Research.pdf
[Copy InputStream]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/ServerStrategy.java#L11
[No-use InputStream]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/ServerStrategy.java#L14
[Single-byte InputStream]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/ServerStrategy.java#L17
[byte array]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/ServerStrategy.java#L20
[ByteBuffer]:https://github.com/MartinanderssonDotcom/secure-login-file-transfer/blob/master/Library/src/main/java/martinandersson/com/library/ServerStrategy.java#L23
