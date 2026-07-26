
public class CartItem {
    private int id;
    private int userId;
    private int medicineId;
    private int quantity;

    // Helper fields for joined database details
    private String medicineName;
    private String medicineBrand;
    private double price;

    public CartItem() {}

    public CartItem(int id, int userId, int medicineId, int quantity) {
        this.id = id;
        this.userId = userId;
        this.medicineId = medicineId;
        this.quantity = quantity;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(int medicineId) {
        this.medicineId = medicineId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getMedicineBrand() {
        return medicineBrand;
    }

    public void setMedicineBrand(String medicineBrand) {
        this.medicineBrand = medicineBrand;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Serializes cart item to JSON, merging joined medicine fields if populated.
     */
    public String toJson() {
        return String.format(
            java.util.Locale.US,
            "{\"id\":%d,\"userId\":%d,\"medicineId\":%d,\"quantity\":%d" +
            (medicineName != null ? ",\"medicineName\":\"" + escapeJson(medicineName) + "\"" : "") +
            (medicineBrand != null ? ",\"medicineBrand\":\"" + escapeJson(medicineBrand) + "\"" : "") +
            (price > 0 ? ",\"price\":" + String.format(java.util.Locale.US, "%.2f", price) : "") + "}",
            id, userId, medicineId, quantity
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
