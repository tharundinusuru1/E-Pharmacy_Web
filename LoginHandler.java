import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LoginHandler extends BaseHandler {

    @Override
    protected void execute(HttpExchange exchange) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }

        String body = getRequestBody(exchange);
        Map<String, String> payload = JsonParser.parse(body);

        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || username.isEmpty() ||
            password == null || password.isEmpty()) {
            sendErrorResponse(exchange, 400, "Username and password are required.");
            return;
        }

        String hashedPassword = hashPassword(password);

        Connection conn = DBConnection.getInstance().getConnection();
        String sql = "SELECT id, username, role, email FROM users WHERE username = ? AND password = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        null, // Do not return password hash
                        rs.getString("role"),
                        rs.getString("email")
                    );
                    sendSuccessResponse(exchange, 200, "Login successful.", user.toJson());
                } else {
                    sendErrorResponse(exchange, 401, "Invalid username or password.");
                }
            }
        }
    }


    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error hashing password", ex);
        }
    }
}
