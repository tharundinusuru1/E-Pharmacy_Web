import com.sun.net.httpserver.HttpExchange;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;


public class PrescriptionHandler extends BaseHandler {

    private static final String UPLOAD_DIR_PATH = "uploads/prescriptions";

    @Override
    protected void execute(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            getPrescriptions(exchange);
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            sendErrorResponse(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }

        String body = getRequestBody(exchange);
        Map<String, String> payload = JsonParser.parse(body);

        String userIdStr = payload.get("userId");
        String fileName = payload.get("fileName");
        String fileData = payload.get("fileData"); // Base64 encoded file content

        if (userIdStr == null || userIdStr.isEmpty() ||
            fileName == null || fileName.isEmpty() ||
            fileData == null || fileData.isEmpty()) {
            sendErrorResponse(exchange, 400, "userId, fileName, and base64 encoded fileData are required.");
            return;
        }

        int userId = Integer.parseInt(userIdStr);

        // 1. Check if user exists
        Connection conn = DBConnection.getInstance().getConnection();
        String checkUserSql = "SELECT id FROM users WHERE id = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {
            checkStmt.setInt(1, userId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    sendErrorResponse(exchange, 404, "User not found.");
                    return;
                }
            }
        }

        // 2. Decode file and write it to the upload directory
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(fileData);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, "Invalid fileData. Must be a valid Base64 encoded string.");
            return;
        }

        // Ensure directories exist
        File uploadDirectory = new File(UPLOAD_DIR_PATH);
        if (!uploadDirectory.exists()) {
            boolean created = uploadDirectory.mkdirs();
            if (!created) {
                System.err.println("Failed to create upload directories: " + uploadDirectory.getAbsolutePath());
            }
        }

        // Sanitize filename to prevent directory traversal
        fileName = new File(fileName).getName();
        // Append timestamp to prevent filename collision
        String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
        File targetFile = new File(uploadDirectory, uniqueFileName);

        // Write the decoded bytes to file
        Files.write(targetFile.toPath(), decodedBytes);
        System.out.println("Prescription uploaded and saved to: " + targetFile.getAbsolutePath());

        // 3. Register prescription in the database
        String insertPrescriptionSql = "INSERT INTO prescriptions (user_id, file_path) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertPrescriptionSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setString(2, targetFile.getPath());
            stmt.executeUpdate();

            int prescriptionId = -1;
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    prescriptionId = generatedKeys.getInt(1);
                }
            }

            // Respond with metadata JSON
            String responseData = String.format(
                "{\"prescriptionId\":%d,\"userId\":%d,\"filePath\":\"%s\",\"fileName\":\"%s\"}",
                prescriptionId, userId, escapeJson(targetFile.getPath()), escapeJson(uniqueFileName)
            );
            sendSuccessResponse(exchange, 201, "Prescription uploaded successfully.", responseData);
        }
    }

    private void getPrescriptions(HttpExchange exchange) throws Exception {
        Connection conn = DBConnection.getInstance().getConnection();
        Map<String, String> params = getQueryParams(exchange);
        String idStr = params.get("id");

        if (idStr != null && !idStr.isEmpty()) {
            int id = Integer.parseInt(idStr);
            String sql = "SELECT file_path FROM prescriptions WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String filePath = rs.getString("file_path");
                        File file = new File(filePath);
                        if (file.exists() && file.isFile()) {
                            String contentType = "application/octet-stream";
                            String name = file.getName().toLowerCase();
                            if (name.endsWith(".pdf")) {
                                contentType = "application/pdf";
                            } else if (name.endsWith(".png")) {
                                contentType = "image/png";
                            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                                contentType = "image/jpeg";
                            }

                            byte[] fileBytes = Files.readAllBytes(file.toPath());
                            exchange.getResponseHeaders().set("Content-Type", contentType);
                            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
                            exchange.sendResponseHeaders(200, fileBytes.length);
                            try (java.io.OutputStream os = exchange.getResponseBody()) {
                                os.write(fileBytes);
                            }
                        } else {
                            sendErrorResponse(exchange, 404, "Prescription file not found on server disk.");
                        }
                    } else {
                        sendErrorResponse(exchange, 404, "Prescription record not found.");
                    }
                }
            }
            return;
        }

        // List all prescriptions (for Admin)
        String sql = "SELECT p.id, p.user_id, u.username, p.file_path FROM prescriptions p " +
                     "JOIN users u ON p.user_id = u.id ORDER BY p.id DESC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            StringBuilder jsonArray = new StringBuilder();
            jsonArray.append("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) {
                    jsonArray.append(",");
                }
                int id = rs.getInt("id");
                int userId = rs.getInt("user_id");
                String username = rs.getString("username");
                String filePath = rs.getString("file_path");
                String fileName = new File(filePath).getName();
                
                jsonArray.append(String.format(
                    "{\"id\":%d,\"userId\":%d,\"username\":\"%s\",\"fileName\":\"%s\",\"filePath\":\"%s\"}",
                    id, userId, escapeJson(username), escapeJson(fileName), escapeJson(filePath)
                ));
                first = false;
            }
            jsonArray.append("]");
            sendSuccessResponse(exchange, 200, "Prescriptions retrieved successfully.", jsonArray.toString());
        }
    }
}
