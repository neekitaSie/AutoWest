package controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Callback;
import model.Model;
import model.User;

public class LoginController {
	@FXML
	private TextField name;
	@FXML
	private PasswordField password;
	@FXML
	private Label message;
	@FXML
	private Button login;
	@FXML
	private Button signup;
	
	private Model model;
	private Stage stage;
	
	public LoginController(Stage stage, Model model) {
		this.stage = stage;
		this.model = model;
	}
	
	@FXML
	public void initialize() {		
		login.setOnAction(event -> {
			// Username and password validation
			if (!name.getText().isEmpty() && !password.getText().isEmpty()) {
				User user;
				try {
					// Calculating the SHA-512 hash for password
					MessageDigest md = MessageDigest.getInstance("SHA-512");
					byte[] hashedPassword = md.digest(password.getText().getBytes(StandardCharsets.UTF_8));
					user = model.getUserDao().getUser(name.getText(), new String(hashedPassword,StandardCharsets.UTF_8));
					if (user != null) {
						model.setCurrentUser(user);
						try {
							//Showing the home view
							FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/HomeView.fxml"));
							HomeController homeController = new HomeController(stage, model);
							
							loader.setController(homeController);
							VBox root = loader.load();
								
							homeController.showStage(root);
							stage.close();
						}catch (IOException e) {
							message.setText(e.getMessage());
							e.printStackTrace();
						}
						
					} else {
						// Show error for incorrect password
						message.setText("Wrong username or password");
						message.setTextFill(Color.RED);
					}
				} catch (SQLException e) {
					e.printStackTrace();
					message.setTextFill(Color.RED);
				} catch (NoSuchAlgorithmException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
			} else {
				message.setText("Empty username or password");
				message.setTextFill(Color.RED);
			}
			name.clear();
			password.clear();
		});
		
		signup.setOnAction(event -> {
			try {
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/SignupView.fxml"));
				
				// Customize controller instance
				SignupController signupController =  new SignupController(stage, model);

				loader.setController(signupController);
				VBox root = loader.load();
				
				signupController.showStage(root);
				
				message.setText("");
				name.clear();
				password.clear();
				
				stage.close();
			} catch (IOException e) {
				message.setText(e.getMessage());
			}});
	}
	
	public void showStage(Pane root) {
		Scene scene = new Scene(root, 500, 300);
		stage.setScene(scene);
		stage.setResizable(false);
		stage.setTitle("Welcome");
		stage.show();
	}
}

