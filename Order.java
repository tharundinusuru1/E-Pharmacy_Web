
public class Order {
    private int id;
    private int userId;
    private String shippingAddress;
    private String paymentMethod;
    private double totalPrice;
    private String orderStatus; // 'PENDING', 'SHIPPED', 'DELIVERED', 'CANCELLED'

    public Order() {}

    public Order(int id, int userId, String shippingAddress, String paymentMethod, double totalPrice, String orderStatus) {
        this.id = id;
        this.userId = userId;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
        this.totalPrice = totalPrice;
        this.orderStatus = orderStatus;
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

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    /**
     * Serializes Order to JSON.
     */
    public String toJson() {
        return String.format(
            java.util.Locale.US,
            "{\"id\":%d,\"userId\":%d,\"shippingAddress\":\"%s\",\"paymentMethod\":\"%s\",\"totalPrice\":%.2f,\"orderStatus\":\"%s\"}",
            id, userId, escapeJson(shippingAddress), escapeJson(paymentMethod), totalPrice, escapeJson(orderStatus)
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
