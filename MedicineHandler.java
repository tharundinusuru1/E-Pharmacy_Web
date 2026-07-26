import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;


public class MedicineHandler extends BaseHandler {

    @Override
    protected void execute(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method) && "/api/medicines".equals(path)) {
            listMedicines(exchange);
        } else if ("POST".equalsIgnoreCase(method) && "/api/admin/medicines".equals(path)) {
            addOrRestockMedicine(exchange);
        } else {
            sendErrorResponse(exchange, 404, "Endpoint not found or method not supported.");
        }
    }


    private void listMedicines(HttpExchange exchange) throws SQLException, java.io.IOException {
        Connection conn = DBConnection.getInstance().getConnection();
        String sql = "SELECT id, name, brand, price, stock_quantity FROM medicines";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            StringBuilder jsonArray = new StringBuilder();
            jsonArray.append("[");
            boolean first = true;
            
            while (rs.next()) {
                if (!first) {
                    jsonArray.append(",");
                }
                Medicine med = new Medicine(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("brand"),
                    rs.getDouble("price"),
                    rs.getInt("stock_quantity")
                );
                jsonArray.append(med.toJson());
                first = false;
            }
            jsonArray.append("]");
            
            sendSuccessResponse(exchange, 200, "Medicines fetched successfully.", jsonArray.toString());
        }
    }

    /**
     * Adds a new medicine or increases stock of an existing one.
     */
    private void addOrRestockMedicine(HttpExchange exchange) throws Exception {
        String body = getRequestBody(exchange);
        Map<String, String> payload = JsonParser.parse(body);

        String idStr = payload.get("id");
        String name = payload.get("name");
        String brand = payload.get("brand");
        String priceStr = payload.get("price");
        String stockQtyStr = payload.get("stockQuantity");

        Connection conn = DBConnection.getInstance().getConnection();

        // Mode 1: Update/Restock by ID if it is provided
        if (idStr != null && !idStr.isEmpty()) {
            int id = Integer.parseInt(idStr);
            int quantityToAdd = Integer.parseInt(stockQtyStr != null ? stockQtyStr : "0");
            
            if (quantityToAdd < 0) {
                sendErrorResponse(exchange, 400, "Quantity to add cannot be negative.");
                return;
            }

            if (name == null || name.isEmpty() || brand == null || brand.isEmpty() ||
                priceStr == null || priceStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "All fields (name, brand, price) are required for updates.");
                return;
            }

            double price = Double.parseDouble(priceStr);

            String sql = "UPDATE medicines SET name = ?, brand = ?, price = ?, stock_quantity = stock_quantity + ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setString(2, brand);
                stmt.setDouble(3, price);
                stmt.setInt(4, quantityToAdd);
                stmt.setInt(5, id);
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    sendSuccessResponse(exchange, 200, "Medicine details updated successfully.", null);
                } else {
                    sendErrorResponse(exchange, 404, "Medicine with specified ID not found.");
                }
            }
        } 
        // Mode 2: Insert a new medicine
        else {
            if (name == null || name.isEmpty() || brand == null || brand.isEmpty() ||
                priceStr == null || priceStr.isEmpty() || stockQtyStr == null || stockQtyStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "All fields (name, brand, price, stockQuantity) are required for new items.");
                return;
            }

            double price = Double.parseDouble(priceStr);
            int stockQuantity = Integer.parseInt(stockQtyStr);

            String sql = "INSERT INTO medicines (name, brand, price, stock_quantity) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, name);
                stmt.setString(2, brand);
                stmt.setDouble(3, price);
                stmt.setInt(4, stockQuantity);
                
                int rowsInserted = stmt.executeUpdate();
                if (rowsInserted > 0) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int newId = generatedKeys.getInt(1);
                            Medicine newMed = new Medicine(newId, name, brand, price, stockQuantity);
                            sendSuccessResponse(exchange, 201, "Medicine added successfully.", newMed.toJson());
                        }
                    }
                } else {
                    sendErrorResponse(exchange, 500, "Failed to add medicine.");
                }
            }
        }
    }
}
