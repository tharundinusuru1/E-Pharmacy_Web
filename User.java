public class User {
    private int id;
    private String username;
    private String password; // SHA-256 Hashed password
    private String role; // 'USER' or 'ADMIN'
    private String email;

    public User() {}

    public User(int id, String username, String password, String role, String email) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Serializes user details to a JSON format (excluding password for security).
     */
    public String toJson() {
        return String.format(
            "{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"email\":\"%s\"}",
            id, escapeJson(username), escapeJson(role), escapeJson(email)
        );
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
