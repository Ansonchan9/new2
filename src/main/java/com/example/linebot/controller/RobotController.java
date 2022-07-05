package com.example.linebot.controller;

import com.azure.identity.*;
import com.example.linebot.handler.MessageHandler;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import okhttp3.Request;
import org.apache.tomcat.util.codec.binary.Base64;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

@RequestMapping("/")
@RestController
public class RobotController {


	@Value("${line.user.secret}")
	private String LINE_SECRET;
	
	@Autowired
	private MessageHandler messageHandler;
	@GetMapping("/")
	public ResponseEntity test() {
		return new ResponseEntity("首頁", HttpStatus.OK);
	}

	@PostMapping("/callback")
	public ResponseEntity messagingAPI(@RequestHeader("X-Line-Signature") String X_Line_Signature,
			@RequestBody String requestBody) throws UnsupportedEncodingException, IOException {
		if (checkFromLine(requestBody, X_Line_Signature)) {
			System.out.println("驗證通過");
			JSONObject object = new JSONObject(requestBody);
			for (int i = 0; i < object.getJSONArray("events").length(); i++) {
				if (object.getJSONArray("events").getJSONObject(i).getString("type").equals("message")) {
					messageHandler.doAction(object.getJSONArray("events").getJSONObject(i));
				}
			}
			return new ResponseEntity<String>("OK", HttpStatus.OK);
		}
		System.out.println("驗證不通過");
		return new ResponseEntity<String>("Not line platform", HttpStatus.BAD_GATEWAY);
	}

	public boolean checkFromLine(String requestBody, String X_Line_Signature) {
		SecretKeySpec key = new SecretKeySpec(LINE_SECRET.getBytes(), "HmacSHA256");
		Mac mac;
		try {
			mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] source = requestBody.getBytes("UTF-8");
			String signature = Base64.encodeBase64String(mac.doFinal(source));
			if (signature.equals(X_Line_Signature)) {
				return true;
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}
//graph Add user authentication
	private static Properties _properties;
	private static DeviceCodeCredential _deviceCodeCredential;
	private static GraphServiceClient<Request> _userClient;

	public static void initializeGraphForUserAuth(Properties properties, Consumer<DeviceCodeInfo> challenge) throws Exception {
		// Ensure properties isn't null
		if (properties == null) {
			throw new Exception("Properties cannot be null");
		}

		_properties = properties;

		final String clientId = properties.getProperty("app.clientId");
		final String authTenantId = properties.getProperty("app.authTenant");
		final List<String> graphUserScopes = Arrays
				.asList(properties.getProperty("app.graphUserScopes").split(","));

		_deviceCodeCredential = new DeviceCodeCredentialBuilder()
				.clientId(clientId)
				.tenantId(authTenantId)
				.challengeConsumer(challenge)
				.build();

		final TokenCredentialAuthProvider authProvider =
				new TokenCredentialAuthProvider(graphUserScopes, _deviceCodeCredential);

		_userClient = GraphServiceClient.builder()
				.authenticationProvider(authProvider)
				.buildClient();
	}
	private static void initializeGraph(Properties properties) {
		try {
			RobotController.initializeGraphForUserAuth(properties,
					challenge -> System.out.println(challenge.getMessage()));
		} catch (Exception e)
		{
			System.out.println("Error initializing Graph for user auth");
			System.out.println(e.getMessage());
		}
	}
//Get user
	public static User getUser() throws Exception {
		// Ensure client isn't null
		if (_userClient == null) {
			throw new Exception("Graph has not been initialized for user auth");
		}

		return _userClient.me()
			.buildRequest()
			.select("displayName,mail,userPrincipalName")
			.get();
	}

//List inbox
	public static MessageCollectionPage getInbox() throws Exception {
		// Ensure client isn't null
		if (_userClient == null) {
			throw new Exception("Graph has not been initialized for user auth");
		}

		return _userClient.me()
				.mailFolders("inbox")
				.messages()
				.buildRequest()
				.select("from,isRead,receivedDateTime,subject")
				.top(25)
				.orderBy("receivedDateTime DESC")
				.get();
	}

//send mail
	public static void sendMail(String subject, String body, String recipient) throws Exception {
		// Ensure client isn't null
		if (_userClient == null) {
			throw new Exception("Graph has not been initialized for user auth");
		}

		// Create a new message
		final Message message = new Message();
		message.subject = subject;
		message.body = new ItemBody();
		message.body.content = body;
		message.body.contentType = BodyType.TEXT;

		final Recipient toRecipient = new Recipient();
		toRecipient.emailAddress = new EmailAddress();
		toRecipient.emailAddress.address = recipient;
		message.toRecipients = List.of(toRecipient);

		// Send the message
		_userClient.me()
			.sendMail(UserSendMailParameterSet.newBuilder()
					.withMessage(message)
					.build())
			.buildRequest()
			.post();
}



}
