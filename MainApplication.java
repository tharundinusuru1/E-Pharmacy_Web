import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;


public class MainApplication {

    public static void main(String[] args) {
        // Read port from Environment Variable or fallback to 8080
        String portStr = System.getenv().getOrDefault("PORT", "8080");
        int port = 8080;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid PORT specified. Falling back to 8080.");
        }

        System.out.println("Starting E-Pharmacy Backend Server...");


        try {
            System.out.println("Checking database connection...");
            Connection conn = DBConnection.getInstance().getConnection();
            if (conn != null && !conn.isClosed()) {
                System.out.println("Database connectivity verified successfully!");
                seedDefaultAdmin(conn);
            }
        } catch (SQLException e) {
            System.err.println("CRITICAL: Failed to connect to MySQL database at boot time!");
            System.err.println("Make sure MySQL is running, schema.sql is executed, and credentials in DBConnection.java match your environment.");
            e.printStackTrace();
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);


            server.createContext("/api/register", new RegisterHandler());
            server.createContext("/api/login", new LoginHandler());


            MedicineHandler medicineHandler = new MedicineHandler();
            server.createContext("/api/medicines", medicineHandler);
            server.createContext("/api/admin/medicines", medicineHandler);


            CartHandler cartHandler = new CartHandler();
            server.createContext("/api/cart", cartHandler);
            server.createContext("/api/cart/add", cartHandler);
            server.createContext("/api/cart/remove", cartHandler);


            server.createContext("/api/checkout", new CheckoutHandler());
            server.createContext("/api/prescriptions", new PrescriptionHandler());


            server.setExecutor(Executors.newFixedThreadPool(10));

            // Start the server
            server.start();
            System.out.println("=================================================");
            System.out.println("E-Pharmacy server successfully started on port: " + port);
            System.out.println("Base URI: http://localhost:" + port);
            System.out.println("Endpoints available:");
            System.out.println("  - POST   /api/register");
            System.out.println("  - POST   /api/login");
            System.out.println("  - GET    /api/medicines");
            System.out.println("  - POST   /api/admin/medicines");
            System.out.println("  - GET    /api/cart?userId=...");
            System.out.println("  - POST   /api/cart/add");
            System.out.println("  - DELETE /api/cart/remove");
            System.out.println("  - POST   /api/checkout");
            System.out.println("  - POST   /api/prescriptions");
            System.out.println("=================================================");

        } catch (Exception ex) {
            System.err.println("FATAL: Failed to initialize and start the HttpServer.");
            ex.printStackTrace();
        }
    }
    private static void seedDefaultAdmin(Connection conn) {
        String checkAdminSql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkAdminSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("No admin user found. Seeding default administrator account...");
                String defaultAdminSql = "INSERT INTO users (username, password, role, email) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(defaultAdminSql)) {
                    insertStmt.setString(1, "admin");
                    // 'admin' hashed with SHA-256
                    insertStmt.setString(2, "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918");
                    insertStmt.setString(3, "ADMIN");
                    insertStmt.setString(4, "admin@biopharma.com");
                    insertStmt.executeUpdate();
                    System.out.println("Default admin user created successfully!");
                    System.out.println("Credentials -> Username: admin | Password: admin");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error seeding default admin user: " + e.getMessage());
        }
    }
}
