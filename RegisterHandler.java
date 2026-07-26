import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public class RegisterHandler extends BaseHandler {

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
        String email = payload.get("email");
        String role = payload.get("role"); // 'USER' or 'ADMIN'

        // Basic Validations
        if (username == null || username.isEmpty() ||
            password == null || password.isEmpty() ||
            email == null || email.isEmpty()) {
            sendErrorResponse(exchange, 400, "Username, password, and email are required fields.");
            return;
        }

        if (role == null || role.isEmpty()) {
            role = "USER"; // Default role
        } else {
            role = role.toUpperCase();
            if ("ADMIN".equals(role)) {
                sendErrorResponse(exchange, 400, "Admin registration is not permitted. Admin accounts must be created directly in the database.");
                return;
            }
            if (!"USER".equals(role)) {
                sendErrorResponse(exchange, 400, "Invalid role. Allowed values: USER.");
                return;
            }
        }

        String hashedPassword = hashPassword(password);

        try {
            Connection conn = DBConnection.getInstance().getConnection();
            String sql = "INSERT INTO users (username, password, role, email) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, hashedPassword);
                stmt.setString(3, role);
                stmt.setString(4, email);

                int rowsInserted = stmt.executeUpdate();
                if (rowsInserted > 0) {
                    sendSuccessResponse(exchange, 201, "User registered successfully.", null);
                } else {
                    sendErrorResponse(exchange, 500, "Failed to register user.");
                }
            }
        } catch (SQLException e) {
            // Check for duplicate key violation
            if (e.getErrorCode() == 1062 || "23000".equals(e.getSQLState())) {
                sendErrorResponse(exchange, 400, "Username or Email already exists.");
            } else {
                throw e;
            }
        }
    }

    /**
     * Helper to compute SHA-256 hash of a string.
     */
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
