import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class CheckoutHandler extends BaseHandler {

    // Simple inner class to hold temporary cart item details during transaction
    private static class TempCartItem {
        int medicineId;
        int quantity;
        double price;
        String name;
         int currentStock;

        TempCartItem(int medicineId, int quantity, double price, String name, int currentStock) {
            this.medicineId = medicineId;
            this.quantity = quantity;
            this.price = price;
            this.name = name;
            this.currentStock = currentStock;
        }
    }

    @Override
    protected void execute(HttpExchange exchange) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }

        String body = getRequestBody(exchange);
        Map<String, String> payload = JsonParser.parse(body);

        String userIdStr = payload.get("userId");
        String shippingAddress = payload.get("shippingAddress");
        String paymentMethod = payload.get("paymentMethod");

        if (userIdStr == null || userIdStr.isEmpty() ||
            shippingAddress == null || shippingAddress.isEmpty() ||
            paymentMethod == null || paymentMethod.isEmpty()) {
            sendErrorResponse(exchange, 400, "userId, shippingAddress, and paymentMethod are required fields.");
            return;
        }

        int userId = Integer.parseInt(userIdStr);
        Connection conn = DBConnection.getInstance().getConnection();

        // 1. Fetch current cart items for the user with details
        List<TempCartItem> cartItems = new ArrayList<>();
        double totalPrice = 0.0;

        String fetchCartSql = "SELECT c.medicine_id, c.quantity, m.price, m.name, m.stock_quantity " +
                              "FROM cart_items c JOIN medicines m ON c.medicine_id = m.id " +
                              "WHERE c.user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(fetchCartSql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TempCartItem item = new TempCartItem(
                        rs.getInt("medicine_id"),
                        rs.getInt("quantity"),
                        rs.getDouble("price"),
                        rs.getString("name"),
                        rs.getInt("stock_quantity")
                    );
                    cartItems.add(item);
                    totalPrice += (item.price * item.quantity);
                }
            }
        }

        if (cartItems.isEmpty()) {
            sendErrorResponse(exchange, 400, "Cannot checkout: Shopping cart is empty.");
            return;
        }

        // Start transaction block
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 2. Lock medicines for update to check stock levels and prevent race conditions
            String lockMedicineSql = "SELECT stock_quantity FROM medicines WHERE id = ? FOR UPDATE";
            for (TempCartItem item : cartItems) {
                try (PreparedStatement lockStmt = conn.prepareStatement(lockMedicineSql)) {
                    lockStmt.setInt(1, item.medicineId);
                    try (ResultSet rs = lockStmt.executeQuery()) {
                        if (rs.next()) {
                            int latestStock = rs.getInt("stock_quantity");
                            if (latestStock < item.quantity) {
                                throw new SQLException("Insufficient stock for medicine: " + item.name + 
                                                      " (Requested: " + item.quantity + ", Available: " + latestStock + ")");
                            }
                        } else {
                            throw new SQLException("Medicine not found in inventory: ID " + item.medicineId);
                        }
                    }
                }
            }

            // 3. Create the Main Order
            int orderId = -1;
            String insertOrderSql = "INSERT INTO orders (user_id, shipping_address, payment_method, total_price, order_status) " +
                                     "VALUES (?, ?, ?, ?, 'PENDING')";

            try (PreparedStatement orderStmt = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                orderStmt.setInt(1, userId);
                orderStmt.setString(2, shippingAddress);
                orderStmt.setString(3, paymentMethod);
                orderStmt.setDouble(4, totalPrice);
                orderStmt.executeUpdate();

                try (ResultSet generatedKeys = orderStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        orderId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated Order ID.");
                    }
                }
            }

            // 4. Move items from cart to order_items and update stock
            String insertOrderItemSql = "INSERT INTO order_items (order_id, medicine_id, quantity, price_at_purchase) " +
                                         "VALUES (?, ?, ?, ?)";
            String updateStockSql = "UPDATE medicines SET stock_quantity = stock_quantity - ? WHERE id = ?";

            try (PreparedStatement itemStmt = conn.prepareStatement(insertOrderItemSql);
                 PreparedStatement stockStmt = conn.prepareStatement(updateStockSql)) {
                
                for (TempCartItem item : cartItems) {
                    // Insert order item snapshot
                    itemStmt.setInt(1, orderId);
                    itemStmt.setInt(2, item.medicineId);
                    itemStmt.setInt(3, item.quantity);
                    itemStmt.setDouble(4, item.price);
                    itemStmt.addBatch();

                    // Decrement stock
                    stockStmt.setInt(1, item.quantity);
                    stockStmt.setInt(2, item.medicineId);
                    stockStmt.addBatch();
                }
                
                itemStmt.executeBatch();
                stockStmt.executeBatch();
            }

            // 5. Clear user's cart
            String clearCartSql = "DELETE FROM cart_items WHERE user_id = ?";
            try (PreparedStatement clearStmt = conn.prepareStatement(clearCartSql)) {
                clearStmt.setInt(1, userId);
                clearStmt.executeUpdate();
            }

            // Commit transaction
            conn.commit();

            // Send successful response with Order object details
            Order completedOrder = new Order(orderId, userId, shippingAddress, paymentMethod, totalPrice, "PENDING");
            sendSuccessResponse(exchange, 201, "Checkout successful. Order placed.", completedOrder.toJson());

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            sendErrorResponse(exchange, 400, "Transaction failed: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}
