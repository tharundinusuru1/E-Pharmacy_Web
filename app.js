

const BASE_URL = 'http://localhost:8080/api';

// Global Client State
let currentUser = null;
let medicines = [];
let cart = [];
let selectedPrescription = null;

// Page Detection Helper
function getPageName() {
    const path = window.location.pathname.toLowerCase();
    if (path.includes('login.html')) return 'login.html';
    if (path.includes('register.html')) return 'register.html';
    if (path.includes('cart.html')) return 'cart.html';
    if (path.includes('prescription.html')) return 'prescription.html';
    if (path.includes('admin.html')) return 'admin.html';
    return 'index.html'; // Default Catalog
}
const CURRENT_PAGE = getPageName();

// Application Boot
document.addEventListener('DOMContentLoaded', () => {
    initPage();
});

function initPage() {
    const savedUser = localStorage.getItem('biopharma_user');
    
    // Guest Pages Check
    if (CURRENT_PAGE === 'login.html' || CURRENT_PAGE === 'register.html') {
        if (savedUser) {
            window.location.href = 'index.html';
            return;
        }
        
        // Setup guest page listeners
        if (CURRENT_PAGE === 'login.html') {
            const loginForm = document.getElementById('login-form');
            if (loginForm) {
                loginForm.addEventListener('submit', handleLogin);
            }
        } else if (CURRENT_PAGE === 'register.html') {
            const registerForm = document.getElementById('register-form');
            if (registerForm) {
                registerForm.addEventListener('submit', handleRegister);
            }
        }
        return;
    }
    
    // Auth Guard for Authenticated Pages
    if (!savedUser) {
        window.location.href = 'login.html';
        return;
    }
    
    currentUser = JSON.parse(savedUser);
    
    // Role Authorization Guard for Admin page
    if (CURRENT_PAGE === 'admin.html' && currentUser.role !== 'ADMIN') {
        sessionStorage.setItem('biopharma_toast', JSON.stringify({
            message: 'Access Denied: Admin role required.',
            type: 'error'
        }));
        window.location.href = 'index.html';
        return;
    }
    
    // Initialize common UI elements
    setupUIForAuthenticatedUser();
    
    // Check for toasts set from previous page redirects
    checkRedirectToasts();
    
    // Initialize Page-Specific Logic & Listeners
    if (CURRENT_PAGE === 'index.html') {
        fetchMedicines();
    } else if (CURRENT_PAGE === 'cart.html') {
        fetchCart();
        const checkoutForm = document.getElementById('checkout-form');
        if (checkoutForm) {
            checkoutForm.addEventListener('submit', handleCheckout);
        }
    } else if (CURRENT_PAGE === 'prescription.html') {
        setupDragAndDrop();
        const fileInput = document.getElementById('file-input');
        if (fileInput) {
            fileInput.addEventListener('change', handleFileSelection);
        }
        const uploadBtn = document.getElementById('upload-prescription-btn');
        if (uploadBtn) {
            uploadBtn.addEventListener('click', uploadPrescription);
        }
    } else if (CURRENT_PAGE === 'admin.html') {
        fetchMedicines();
        fetchPrescriptions();
        const medForm = document.getElementById('admin-medicine-form');
        if (medForm) {
            medForm.addEventListener('submit', saveMedicine);
        }
    }
}

function checkRedirectToasts() {
    const toastData = sessionStorage.getItem('biopharma_toast');
    if (toastData) {
        try {
            const { message, type } = JSON.parse(toastData);
            showToast(message, type);
        } catch (e) {}
        sessionStorage.removeItem('biopharma_toast');
    }
}

// Adjust view and headers for logged-in user
function setupUIForAuthenticatedUser() {
    const navLinks = document.getElementById('nav-links');
    const navActions = document.getElementById('nav-actions');
    
    if (navLinks) navLinks.style.display = 'flex';
    if (navActions) navActions.style.display = 'flex';
    
    const nameDisplay = document.getElementById('user-display-name');
    if (nameDisplay) nameDisplay.textContent = currentUser.username;
    
    const roleTag = document.getElementById('user-display-role');
    const navCatalog = document.getElementById('nav-catalog');
    const navPrescription = document.getElementById('nav-prescription');

    if (roleTag) {
        roleTag.textContent = currentUser.role;
        if (currentUser.role === 'ADMIN') {
            roleTag.className = 'user-role-tag role-admin';
            const adminNavItem = document.getElementById('admin-nav-item');
            if (adminNavItem) adminNavItem.style.display = 'block';

            // Hide customer-facing Catalog and Prescription links for Admin
            if (navCatalog && navCatalog.parentElement) navCatalog.parentElement.style.display = 'none';
            if (navPrescription && navPrescription.parentElement) navPrescription.parentElement.style.display = 'none';
        } else {
            roleTag.className = 'user-role-tag role-user';
            const adminNavItem = document.getElementById('admin-nav-item');
            if (adminNavItem) adminNavItem.style.display = 'none';

            // Ensure customer-facing Catalog and Prescription links are visible for regular users
            if (navCatalog && navCatalog.parentElement) navCatalog.parentElement.style.display = 'block';
            if (navPrescription && navPrescription.parentElement) navPrescription.parentElement.style.display = 'block';
        }
    }
    
    // Fetch initial state data
    updateCartBadge();
}

// Update Cart Badge Count
function updateCartBadge() {
    if (!currentUser) return;
    
    fetch(`${BASE_URL}/cart?userId=${currentUser.id}`)
        .then(res => res.json())
        .then(data => {
            if (data.success && data.data) {
                const totalCount = data.data.reduce((sum, item) => sum + item.quantity, 0);
                const cartCountEl = document.getElementById('cart-count');
                if (cartCountEl) cartCountEl.textContent = totalCount;
            }
        })
        .catch(err => console.error("Error updating cart badge:", err));
}

// Handler: User Registration
function handleRegister(e) {
    e.preventDefault();
    const username = document.getElementById('reg-username').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value;

    const payload = { username, email, password, role: 'USER' }; // Lock role to USER on registration

    fetch(`${BASE_URL}/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            sessionStorage.setItem('biopharma_toast', JSON.stringify({
                message: "Registration successful! Please login.",
                type: "success"
            }));
            window.location.href = 'login.html';
        } else {
            showToast(data.message || "Registration failed.", "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Server unreachable. Please try again.", "error");
    });
}

// Handler: User Login
function handleLogin(e) {
    e.preventDefault();
    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value;

    const payload = { username, password };

    fetch(`${BASE_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success && data.data) {
            currentUser = data.data;
            localStorage.setItem('biopharma_user', JSON.stringify(currentUser));
            sessionStorage.setItem('biopharma_toast', JSON.stringify({
                message: `Welcome back, ${currentUser.username}!`,
                type: "success"
            }));
            window.location.href = 'index.html';
        } else {
            showToast(data.message || "Invalid credentials.", "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Server unreachable. Please check backend connection.", "error");
    });
}

// Handler: User Logout
function handleLogout() {
    currentUser = null;
    localStorage.removeItem('biopharma_user');
    window.location.href = 'login.html';
}

// Fetch Inventory Catalog
function fetchMedicines() {
    fetch(`${BASE_URL}/medicines`)
        .then(res => res.json())
        .then(data => {
            if (data.success && data.data) {
                medicines = data.data;
                if (CURRENT_PAGE === 'index.html') {
                    renderCatalog();
                } else if (CURRENT_PAGE === 'admin.html') {
                    renderAdminInventory();
                }
            }
        })
        .catch(err => {
            console.error("Error fetching medicines:", err);
            showToast("Failed to fetch medicines catalog.", "error");
        });
}

// Render Catalog Cards
function renderCatalog(filterTerm = '') {
    const grid = document.getElementById('meds-grid');
    if (!grid) return;
    grid.innerHTML = '';
    
    const filtered = medicines.filter(med => 
        med.name.toLowerCase().includes(filterTerm.toLowerCase()) || 
        med.brand.toLowerCase().includes(filterTerm.toLowerCase())
    );

    if (filtered.length === 0) {
        grid.innerHTML = `
            <div class="empty-state" style="grid-column: 1/-1;">
                <i class="fa-solid fa-box-open empty-state-icon"></i>
                <p>No medicines found matching your search.</p>
            </div>
        `;
        return;
    }

    filtered.forEach(med => {
        const card = document.createElement('div');
        card.className = 'med-card';
        
        let stockIndicatorClass = 'stock-in';
        let stockText = `${med.stockQuantity} items in stock`;
        let disableBtn = false;

        if (med.stockQuantity === 0) {
            stockIndicatorClass = 'stock-out';
            stockText = 'Out of Stock';
            disableBtn = true;
        } else if (med.stockQuantity < 10) {
            stockIndicatorClass = 'stock-low';
            stockText = `Low stock: ${med.stockQuantity} left`;
        }

        card.innerHTML = `
            <div>
                <span class="med-brand">${med.brand}</span>
                <h3 class="med-name">${med.name}</h3>
                <div class="med-stock">
                    <span class="stock-indicator ${stockIndicatorClass}"></span>
                    <span>${stockText}</span>
                </div>
            </div>
            <div>
                <div class="med-price">${med.price.toFixed(2)}</div>
                <button class="btn btn-primary" style="width: 100%;" onclick="addToCart(${med.id})" ${disableBtn ? 'disabled' : ''}>
                    <i class="fa-solid fa-cart-plus"></i> Add to Cart
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

// Filter medicines input wrapper
function filterMedicines() {
    const val = document.getElementById('search-input').value;
    renderCatalog(val);
}

// POST Add to Cart
function addToCart(medicineId) {
    if (!currentUser) {
        window.location.href = 'login.html';
        return;
    }

    const payload = {
        userId: currentUser.id.toString(),
        medicineId: medicineId.toString(),
        quantity: "1"
    };

    fetch(`${BASE_URL}/cart/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast("Item added to cart successfully!", "success");
            updateCartBadge();
        } else {
            showToast(data.message || "Failed to add item.", "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Could not contact cart service.", "error");
    });
}

// Fetch Cart View List
function fetchCart() {
    if (!currentUser) return;

    fetch(`${BASE_URL}/cart?userId=${currentUser.id}`)
        .then(res => res.json())
        .then(data => {
            if (data.success && data.data) {
                cart = data.data;
                renderCart();
            }
        })
        .catch(err => {
            console.error(err);
            showToast("Failed to retrieve shopping cart items.", "error");
        });
}

// Render Cart items list
function renderCart() {
    const list = document.getElementById('cart-items-list');
    const summaryCard = document.getElementById('checkout-summary-card');
    if (!list) return;
    list.innerHTML = '';

    if (cart.length === 0) {
        list.innerHTML = `
            <div class="empty-state">
                <i class="fa-solid fa-shopping-basket empty-state-icon"></i>
                <p>Your shopping cart is empty.</p>
                <button class="btn btn-primary" onclick="window.location.href='index.html'" style="margin-top: 1rem;">
                    Browse Catalog
                </button>
            </div>
        `;
        if (summaryCard) summaryCard.style.display = 'none';
        return;
    }

    if (summaryCard) summaryCard.style.display = 'block';
    let total = 0;

    cart.forEach(item => {
        const subtotal = item.price * item.quantity;
        total += subtotal;

        const row = document.createElement('div');
        row.className = 'cart-item-row';
        row.innerHTML = `
            <div class="cart-item-details">
                <div class="cart-item-name">${item.medicineName}</div>
                <div class="cart-item-brand">${item.medicineBrand}</div>
                <div class="cart-item-price-info">$${item.price.toFixed(2)} each</div>
            </div>
            
            <div class="cart-qty-control">
                <button class="qty-btn" onclick="changeQuantity(${item.id}, ${item.medicineId}, ${item.quantity}, -1)">-</button>
                <span class="qty-val">${item.quantity}</span>
                <button class="qty-btn" onclick="changeQuantity(${item.id}, ${item.medicineId}, ${item.quantity}, 1)">+</button>
            </div>

            <div class="cart-item-subtotal">$${subtotal.toFixed(2)}</div>
            
            <button class="btn btn-danger" style="padding: 0.5rem 0.8rem;" onclick="removeCartItem(${item.id})">
                <i class="fa-solid fa-trash-can"></i>
            </button>
        `;
        list.appendChild(row);
    });

    const totalPriceEl = document.getElementById('cart-total-price');
    if (totalPriceEl) totalPriceEl.textContent = `$${total.toFixed(2)}`;
}

// Atomic Cart Quantity Adjustment using Add/Remove endpoints
function changeQuantity(cartItemId, medicineId, currentQty, delta) {
    if (delta > 0) {
        // Increment: simply add another one
        addToCart(medicineId);
        setTimeout(fetchCart, 200); // refresh cart
    } else {
        // Decrement:
        if (currentQty === 1) {
            // Delete completely
            removeCartItem(cartItemId);
        } else {
            // Delete all and re-add currentQty - 1
            const removePayload = { id: cartItemId.toString() };
            fetch(`${BASE_URL}/cart/remove`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(removePayload)
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    // Re-add quantity-1
                    const addPayload = {
                        userId: currentUser.id.toString(),
                        medicineId: medicineId.toString(),
                        quantity: (currentQty - 1).toString()
                    };
                    return fetch(`${BASE_URL}/cart/add`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(addPayload)
                    });
                }
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    fetchCart();
                    updateCartBadge();
                }
            })
            .catch(err => console.error(err));
        }
    }
}

// Remove item completely from cart
function removeCartItem(cartItemId) {
    const payload = { id: cartItemId.toString() };

    fetch(`${BASE_URL}/cart/remove`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast("Item removed from cart.", "success");
            fetchCart();
            updateCartBadge();
        } else {
            showToast("Failed to remove item.", "error");
        }
    })
    .catch(err => console.error(err));
}

// Handle Order Checkout Submission
function handleCheckout(e) {
    e.preventDefault();
    const address = document.getElementById('shipping-address').value.trim();
    const payment = document.getElementById('payment-method').value;

    const payload = {
        userId: currentUser.id.toString(),
        shippingAddress: address,
        paymentMethod: payment
    };

    fetch(`${BASE_URL}/checkout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            sessionStorage.setItem('biopharma_toast', JSON.stringify({
                message: "Checkout successful! Order placed.",
                type: "success"
            }));
            window.location.href = 'index.html';
        } else {
            showToast(data.message || "Checkout transaction failed.", "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Failed to process checkout transaction.", "error");
    });
}

// Setup Drag & Drop Upload Zone events
function setupDragAndDrop() {
    const area = document.getElementById('drag-area');
    
    if (!area) return;

    ['dragenter', 'dragover'].forEach(eventName => {
        area.addEventListener(eventName, (e) => {
            e.preventDefault();
            area.classList.add('dragover');
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        area.addEventListener(eventName, (e) => {
            e.preventDefault();
            area.classList.remove('dragover');
        }, false);
    });

    area.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files.length > 0) {
            processPrescriptionFile(files[0]);
        }
    }, false);

    area.addEventListener('click', triggerFileSelect);
}

function triggerFileSelect() {
    const fileInput = document.getElementById('file-input');
    if (fileInput) fileInput.click();
}

function handleFileSelection(e) {
    const files = e.target.files;
    if (files.length > 0) {
        processPrescriptionFile(files[0]);
    }
}

// Convert uploaded file to base64 encoding
function processPrescriptionFile(file) {
    if (file.size > 5 * 1024 * 1024) {
        showToast("File size too large (Max 5MB allowed).", "error");
        return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
        const base64Data = e.target.result.split(',')[1];
        
        selectedPrescription = {
            fileName: file.name,
            fileSize: (file.size / 1024).toFixed(1) + " KB",
            fileData: base64Data
        };

        // Render preview card
        const nameEl = document.getElementById('preview-file-name');
        const sizeEl = document.getElementById('preview-file-size');
        const previewCard = document.getElementById('prescription-preview');
        const previewIcon = document.getElementById('preview-file-icon');

        if (nameEl) nameEl.textContent = file.name;
        if (sizeEl) sizeEl.textContent = (file.size / 1024).toFixed(1) + " KB";
        
        if (previewIcon) {
            if (file.type.includes('image')) {
                previewIcon.className = "fa-solid fa-file-image";
                previewIcon.style.color = "var(--accent-primary)";
            } else {
                previewIcon.className = "fa-solid fa-file-pdf";
                previewIcon.style.color = "var(--accent-danger)";
            }
        }
        
        if (previewCard) previewCard.style.display = 'block';
    };
    reader.readAsDataURL(file);
}

// Upload decoded base64 prescription
function uploadPrescription() {
    if (!selectedPrescription || !currentUser) return;

    const payload = {
        userId: currentUser.id.toString(),
        fileName: selectedPrescription.fileName,
        fileData: selectedPrescription.fileData
    };

    fetch(`${BASE_URL}/prescriptions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast("Prescription uploaded successfully!", "success");
            const previewCard = document.getElementById('prescription-preview');
            if (previewCard) previewCard.style.display = 'none';
            selectedPrescription = null;
        } else {
            showToast(data.message || "Failed to upload prescription.", "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Prescription upload service error.", "error");
    });
}

// Render Admin Inventory Overview Table
function renderAdminInventory() {
    const tableBody = document.getElementById('admin-inventory-table');
    if (!tableBody) return;
    
    tableBody.innerHTML = '';
    
    medicines.forEach(med => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>
                <div style="font-weight:600;">${med.name}</div>
                <div style="font-size:0.75rem; color:var(--text-secondary);">${med.brand}</div>
            </td>
            <td>$${med.price.toFixed(2)}</td>
            <td>
                <span class="stock-indicator ${med.stockQuantity > 10 ? 'stock-in' : med.stockQuantity > 0 ? 'stock-low' : 'stock-out'}"></span>
                ${med.stockQuantity}
            </td>
            <td>
                <div style="display:flex; gap:0.5rem;">
                    <button class="btn btn-primary" style="padding:0.3rem 0.6rem; font-size:0.8rem;" onclick="loadMedicineForRestock(${med.id}, '${med.name}', '${med.brand}', ${med.price})">
                        <i class="fa-solid fa-plus-circle"></i> Restock
                    </button>
                    <button class="btn" style="padding:0.3rem 0.6rem; font-size:0.8rem;" onclick="loadMedicineForEdit(${med.id}, '${med.name}', '${med.brand}', ${med.price})">
                        <i class="fa-solid fa-edit"></i> Edit
                    </button>
                </div>
            </td>
        `;
        tableBody.appendChild(row);
    });
}

// Load Medicine detail for restocking
function loadMedicineForRestock(id, name, brand, price) {
    document.getElementById('admin-form-title').textContent = `Restock - ${name}`;
    document.getElementById('med-id').value = id;
    
    const nameField = document.getElementById('med-name');
    const brandField = document.getElementById('med-brand');
    const priceField = document.getElementById('med-price');
    const stockField = document.getElementById('med-stock');
    
    nameField.value = name;
    nameField.readOnly = true;
    brandField.value = brand;
    brandField.readOnly = true;
    priceField.value = price;
    priceField.readOnly = true;
    
    stockField.value = '';
    stockField.focus();
}

// Load Medicine detail for editing price/new addition
function loadMedicineForEdit(id, name, brand, price) {
    document.getElementById('admin-form-title').textContent = `Edit Product - ${name}`;
    document.getElementById('med-id').value = id;
    
    const nameField = document.getElementById('med-name');
    const brandField = document.getElementById('med-brand');
    const priceField = document.getElementById('med-price');
    const stockField = document.getElementById('med-stock');
    
    nameField.value = name;
    nameField.readOnly = false;
    brandField.value = brand;
    brandField.readOnly = false;
    priceField.value = price;
    priceField.readOnly = false;
    
    stockField.value = '0';
}

// Reset admin fields
function resetAdminForm() {
    document.getElementById('admin-form-title').textContent = "Add New Medicine";
    document.getElementById('admin-medicine-form').reset();
    document.getElementById('med-id').value = '';
    
    document.getElementById('med-name').readOnly = false;
    document.getElementById('med-brand').readOnly = false;
    document.getElementById('med-price').readOnly = false;
}

// Save/Update inventory API wrapper
function saveMedicine(e) {
    e.preventDefault();
    const id = document.getElementById('med-id').value;
    const name = document.getElementById('med-name').value.trim();
    const brand = document.getElementById('med-brand').value.trim();
    const price = document.getElementById('med-price').value;
    const stockQuantity = document.getElementById('med-stock').value;

    const payload = {};
    if (id) payload.id = id.toString();
    payload.name = name;
    payload.brand = brand;
    payload.price = price.toString();
    payload.stockQuantity = stockQuantity.toString();

    fetch(`${BASE_URL}/admin/medicines`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast("Medicine inventory updated successfully!", "success");
            resetAdminForm();
            fetchMedicines();
        } else {
            showToast(data.message || "Failed to save medicine.", "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Error updating stock catalog.", "error");
    });
}

// ================= ADMIN PRESCRIPTIONS VIEWER =================

function fetchPrescriptions() {
    fetch(`${BASE_URL}/prescriptions`)
        .then(res => res.json())
        .then(data => {
            if (data.success && data.data) {
                renderPrescriptions(data.data);
            }
        })
        .catch(err => {
            console.error("Error fetching prescriptions:", err);
            showToast("Failed to retrieve customer prescriptions.", "error");
        });
}

function renderPrescriptions(presList) {
    const tbody = document.getElementById('admin-prescriptions-table');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (presList.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="4" style="text-align: center; color: var(--text-secondary); padding: 2rem;">
                    No uploaded prescriptions found.
                </td>
            </tr>
        `;
        return;
    }
    
    presList.forEach(pres => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>#${pres.id}</td>
            <td>
                <div style="font-weight:600;">${pres.username}</div>
                <div style="font-size:0.75rem; color:var(--text-secondary);">User ID: ${pres.userId}</div>
            </td>
            <td>
                <div style="display:flex; align-items:center; gap:0.5rem;">
                    <i class="fa-solid fa-file-invoice" style="color:var(--accent-primary);"></i>
                    <span>${pres.fileName}</span>
                </div>
            </td>
            <td>
                <button class="btn btn-primary" style="padding:0.3rem 0.6rem; font-size:0.8rem;" onclick="viewPrescription(${pres.id})">
                    <i class="fa-solid fa-eye"></i> View File
                </button>
            </td>
        `;
        tbody.appendChild(row);
    });
}

function viewPrescription(prescriptionId) {
    window.open(`${BASE_URL}/prescriptions?id=${prescriptionId}`, '_blank');
}

// Tab switcher for Admin panel
window.switchAdminTab = function(tab) {
    const invTab = document.getElementById('admin-tab-inventory');
    const presTab = document.getElementById('admin-tab-prescriptions');
    const invSec = document.getElementById('admin-inventory-section');
    const presSec = document.getElementById('admin-prescriptions-section');
    
    if (!invTab || !presTab || !invSec || !presSec) return;

    if (tab === 'inventory') {
        invTab.classList.add('btn-primary');
        invTab.style.background = '';
        invTab.style.color = '';
        invTab.style.border = '';
        
        presTab.classList.remove('btn-primary');
        presTab.style.background = 'rgba(255,255,255,0.05)';
        presTab.style.color = 'var(--text-primary)';
        presTab.style.border = '1px solid var(--border-color)';
        
        invSec.style.display = 'grid';
        presSec.style.display = 'none';
    } else {
        presTab.classList.add('btn-primary');
        presTab.style.background = '';
        presTab.style.color = '';
        presTab.style.border = '';
        
        invTab.classList.remove('btn-primary');
        invTab.style.background = 'rgba(255,255,255,0.05)';
        invTab.style.color = 'var(--text-primary)';
        invTab.style.border = '1px solid var(--border-color)';
        
        invSec.style.display = 'none';
        presSec.style.display = 'block';
        fetchPrescriptions(); // Refresh the prescriptions list
    }
};

// Toast Slide Notification Helper
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let icon = '<i class="fa-solid fa-info-circle"></i>';
    if (type === 'success') {
        icon = '<i class="fa-solid fa-circle-check"></i>';
    } else if (type === 'error') {
        icon = '<i class="fa-solid fa-circle-exclamation"></i>';
    }

    toast.innerHTML = `
        ${icon}
        <span>${message}</span>
    `;

    container.appendChild(toast);
    
    // Auto-remove after 4 seconds
    setTimeout(() => {
        toast.style.animation = 'slideIn 0.3s ease reverse forwards';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}