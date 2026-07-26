import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;


public class CartHandler extends BaseHandler {

    @Override
    protected void execute(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method) && "/api/cart/add".equals(path)) {
            addToCart(exchange);
        } else if ("GET".equalsIgnoreCase(method) && "/api/cart".equals(path)) {
            getCart(exchange);
        } else if ("DELETE".equalsIgnoreCase(method) && "/api/cart/remove".equals(path)) {
            removeFromCart(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Endpoint not found or method not supported.");
        }
    }

    private void addToCart(HttpExchange exchange) throws Exception {
        String body = getRequestBody(exchange);
        Map<String, String> payload = JsonParser.parse(body);

        String userIdStr = payload.get("userId");
        String medicineIdStr = payload.get("medicineId");
        String qtyStr = payload.get("quantity");

        if (userIdStr == null || userIdStr.isEmpty() || medicineIdStr == null || medicineIdStr.isEmpty()) {
            sendErrorResponse(exchange, 400, "userId and medicineId are required.");
            return;
        }

        int userId = Integer.parseInt(userIdStr);
        int medicineId = Integer.parseInt(medicineIdStr);
        int quantity = Integer.parseInt(qtyStr != null ? qtyStr : "1");

        if (quantity <= 0) {
            sendErrorResponse(exchange, 400, "Quantity must be greater than zero.");
            return;
        }

        Connection conn = DBConnection.getInstance().getConnection();

        // Check if medicine exists and has sufficient stock
        String checkStockSql = "SELECT stock_quantity FROM medicines WHERE id = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkStockSql)) {
            checkStmt.setInt(1, medicineId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    sendErrorResponse(exchange, 404, "Medicine not found.");
                    return;
                }
                int stock = rs.getInt("stock_quantity");
                if (stock < quantity) {
                    sendErrorResponse(exchange, 400, "Insufficient stock. Only " + stock + " items available.");
                    return;
                }
            }
        }

        
        String sql = "INSERT INTO cart_items (user_id, medicine_id, quantity) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE quantity = quantity + ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, medicineId);
            stmt.setInt(3, quantity);
            stmt.setInt(4, quantity);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                sendSuccessResponse(exchange, 200, "Item added/updated in cart successfully.", null);
            } else {
                sendErrorResponse(exchange, 500, "Failed to add item to cart.");
            }
        }
    }

    
    private void getCart(HttpExchange exchange) throws SQLException, java.io.IOException {
        Map<String, String> params = getQueryParams(exchange);
        String userIdStr = params.get("userId");

        if (userIdStr == null || userIdStr.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing required query parameter: userId.");
            return;
        }

        int userId = Integer.parseInt(userIdStr);
        Connection conn = DBConnection.getInstance().getConnection();

        String sql = "SELECT c.id, c.user_id, c.medicine_id, c.quantity, m.name, m.brand, m.price " +
                     "FROM cart_items c " +
                     "JOIN medicines m ON c.medicine_id = m.id " +
                     "WHERE c.user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                StringBuilder jsonArray = new StringBuilder();
                jsonArray.append("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        jsonArray.append(",");
                    }
                    CartItem item = new CartItem(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getInt("medicine_id"),
                        rs.getInt("quantity")
                    );
                    item.setMedicineName(rs.getString("name"));
                    item.setMedicineBrand(rs.getString("brand"));
                    item.setPrice(rs.getDouble("price"));

                    jsonArray.append(item.toJson());
                    first = false;
                }
                jsonArray.append("]");
                sendSuccessResponse(exchange, 200, "Cart items retrieved successfully.", jsonArray.toString());
            }
        }
    }

    
    private void removeFromCart(HttpExchange exchange) throws Exception {
        String body = getRequestBody(exchange);
        Map<String, String> payload = JsonParser.parse(body);

        String idStr = payload.get("id");
        String userIdStr = payload.get("userId");
        String medicineIdStr = payload.get("medicineId");

        Connection conn = DBConnection.getInstance().getConnection();
        int rowsDeleted = 0;

        // Mode 1: Delete specific cart item by ID
        if (idStr != null && !idStr.isEmpty()) {
            int id = Integer.parseInt(idStr);
            String sql = "DELETE FROM cart_items WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                rowsDeleted = stmt.executeUpdate();
            }
        } 
        // Mode 2: Delete by userId and medicineId combination
        else if (userIdStr != null && !userIdStr.isEmpty() && medicineIdStr != null && !medicineIdStr.isEmpty()) {
            int userId = Integer.parseInt(userIdStr);
            int medicineId = Integer.parseInt(medicineIdStr);
            String sql = "DELETE FROM cart_items WHERE user_id = ? AND medicine_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, medicineId);
                rowsDeleted = stmt.executeUpdate();
            }
        } else {
            sendErrorResponse(exchange, 400, "Either 'id' or both 'userId' and 'medicineId' must be provided in the request body.");
            return;
        }

        if (rowsDeleted > 0) {
            sendSuccessResponse(exchange, 200, "Item removed from cart successfully.", null);
        } else {
            sendErrorResponse(exchange, 404, "Cart item not found.");
        }
    }
}
