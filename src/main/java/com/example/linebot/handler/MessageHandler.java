package com.example.linebot.handler;

import com.example.linebot.controller.RobotController;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.MessageCollectionPage;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Properties;

@Component
public class MessageHandler {
	private OkHttpClient client = new OkHttpClient();
	@Value("${line.user.channel.token}")
	private String LINE_TOKEN;

	public void doAction(JSONObject event) {
		switch (event.getJSONObject("message").getString("type")) {
		case "text":
			text(event.getString("replyToken"), event.getJSONObject("message").getString("text"));
			break;
		case "sticker":
			sticker(event.getString("replyToken"), event.getJSONObject("message").getString("packageId"),
					event.getJSONObject("message").getString("stickerId"));
			break;
		}
	}


	private void text(String replyToken, String text) {
		JSONObject body = new JSONObject();
		JSONArray messages = new JSONArray();
		JSONObject message = new JSONObject();
		message.put("type", "text");
		switch (text) {
			case "你好":
				message.put("text", "哈囉");
				break;

			case "2":
				greetUser();
				break;
			case "3":
				listInbox();
				break;
			case "4":
				sendMail();
			default:
				message.put("text", "我還看不懂");
			break;
		}
		messages.put(message);
		body.put("replyToken", replyToken);
		body.put("messages", messages);
		sendLinePlatform(body);

	}

	private void sticker(String replyToken, String packageId, String stickerId) {
		JSONObject body = new JSONObject();
		JSONArray messages = new JSONArray();
		JSONObject message = new JSONObject();
		message.put("type", "sticker");
		message.put("packageId", packageId);
		message.put("stickerId", stickerId);
		messages.put(message);
		body.put("replyToken", replyToken);
		body.put("messages", messages);
		sendLinePlatform(body);
	}

	public void sendLinePlatform(JSONObject json) {
		Request request = new Request.Builder().url("https://api.line.me/v2/bot/message/reply")
				.header("Authorization", "Bearer {" + LINE_TOKEN + "}")
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString())).build();
		client.newCall(request).enqueue(new Callback() {

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				System.out.println(response.body());
			}

			@Override
			public void onFailure(Call call, IOException e) {
				System.err.println(e);
			}
		});
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
	private static void greetUser() {
		try {
			final User user = RobotController.getUser();
			// For Work/school accounts, email is in mail property
			// Personal accounts, email is in userPrincipalName
			final String email = user.mail == null ? user.userPrincipalName : user.mail;
			System.out.println("Hello, " + user.displayName + "!");
			System.out.println("Email: " + email);
		} catch (Exception e) {
			System.out.println("Error getting user");
			System.out.println(e.getMessage());
		}
	}
	private static void listInbox() {
		try {
			final MessageCollectionPage messages = RobotController.getInbox();

			// Output each message's details
			for (Message message: messages.getCurrentPage()) {
				System.out.println("Message: " + message.subject);
				System.out.println("  From: " + message.from.emailAddress.name);
				System.out.println("  Status: " + (message.isRead ? "Read" : "Unread"));
				System.out.println("  Received: " + message.receivedDateTime
						// Values are returned in UTC, convert to local time zone
						.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
						.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)));
			}

			final Boolean moreMessagesAvailable = messages.getNextPage() != null;
			System.out.println("\nMore messages available? " + moreMessagesAvailable);
		} catch (Exception e) {
			System.out.println("Error getting inbox");
			System.out.println(e.getMessage());
		}
	}
	private static void sendMail() {
		try {
			// Send mail to the signed-in user
			// Get the user for their email address
			final User user = RobotController.getUser();
			final String email = user.mail == null ? user.userPrincipalName : user.mail;

			RobotController.sendMail("Testing Microsoft Graph", "Hello world!", email);
			System.out.println("\nMail sent.");
		} catch (Exception e) {
			System.out.println("Error sending mail");
			System.out.println(e.getMessage());
		}
	}
}
