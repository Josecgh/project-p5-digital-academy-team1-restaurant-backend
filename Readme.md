# GitSushi Restaurant

GitSushi is a team project developed as part of the Factoría F5 bootcamp. Its objective is to create a restaurant application using a decoupled architecture for the frontend and backend.

## 🗂️ Project Structure

The application is divided into two separate repositories:

- **Frontend repository:** [GitSushi Restaurant Frontend](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-frontend)
- **Backend repository:** [GitSushi Restaurant Backend](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend)

## ✨ Features

### 👥 Customers
* **Order Placement:** Customers must be able to place their orders either on-site using available tablets or from home via the application.
* **Shopping Cart:** Selected products must be added to a temporary cart where users can add or remove items.
* **Quantity Selection:** When selecting a product, the customer must have the option to add more than one unit.
* **On-Site Payment:** For orders placed on-site via tablets, customers must be able to pay at the cashier counter or by credit/debit card.
* **Online Payment:** If the order is placed online (prior registration is required), customers must be able to pay by credit/debit card or choose cash on delivery.
* **Order Status:** Customers must have access to a section showing their real-time order status, which is managed by the kitchen.
* **Accounts & Offers:** Customers must be able to create an account to access exclusive offers.
* **Profile Details:** Registered customers are required to fill out a profile containing the following information: `First Name`, `Last Name`, `Address`, `Postal Code`, `City`, and `Email`.
* **Voice Input:** In the profile setup form, the `City` field must support voice-to-text input.
* **Order Tracking:** A tracking system must monitor deliveries.

### 🍳 Kitchen
* **Management Interface:** The kitchen staff must have access to a dedicated dashboard for order management and fulfillment.
* **Status Updates:** Kitchen staff must be able to update the order status to:
  - [ ] `In Progress`
  - [ ] `Delayed`
  - [ ] `Ready`

### 🛵 Delivery Drivers
* **Driver Interface:** Delivery drivers must have access to a mobile-friendly dashboard to update delivery statuses.
* **Status Tracking:** Drivers must be able to mark orders as:
  - [ ] `In Transit`
  - [ ] `Delivered`
* **Notifications:** Once an order is marked as `In Transit`, the customer must automatically receive a notification email.

### 💼 Administration
* **Admin Interface:** The administration team must have access to a comprehensive dashboard for managing the platform.
* **Product Management:** Full CRUD operations for products, including at least the following categories: *Drinks*, *Specialties*, and *Desserts*. This includes:
  - Adding new products to the menu.
  - Disabling products temporarily due to stock shortages.
  - Removing products from the menu entirely.
* **Invoicing:** Once a customer completes payment, the order status must be updated to `Paid`, and the transaction details must be logged in an invoicing table.
* **Sales Reports:** Administrators must have the ability to download sales summaries.
* **Analytics:** The admin dashboard must display the following sales data:
  - Daily sales totals
  - Monthly sales totals
  - Quarterly sales totals
  - Annual sales totals

### ⚙️ System
* **Automated Reports:** The system must automatically generate a sales summary report in **PDF format**.
* **Cloud Storage:** Generated summaries must be securely saved to a cloud object storage service (e.g., *Firebase*, *Supabase*).

## 📊 Project Planning and Management

This project is developed using **Agile methodologies** to ensure efficient delivery and high code quality across our decoupled repositories.

### 🔄 Agile Implementation
* **Product Backlog:** A clear, well-defined, and strictly prioritized backlog mapping out all system requirements (Customers, Kitchen, Drivers, Admin, and System Automation).
* **Sprint Organization:** Development is divided into two sprints.
  - **Jira** is used for sprint planning, user stories, task distribution, and progress tracking.

## 🛠️ Backend Best Practices
- **Data Validation:** Strict validation of all incoming request payloads.
- **Structured Responses:** Consistent and properly formatted HTTP response structures.
- **Middleware:** Middleware layers for security, logging, and request processing.
- **Authentication and Authorization:** Secure authentication using robust token systems (e.g., JWT).
- **Well-Defined Routing:** Clear, intuitive, and RESTful API endpoints.
- **User Management System:** A complete system for managing user registration, roles, and profiles.
- **Password Hashing:** Secure password storage using advanced hashing algorithms (e.g., bcrypt, Argon2).
- **Architecture Patterns:** A clean architecture using controllers, services, and repositories.
- **File & Image Handling:** Efficient processing, upload management, and storage of media files.

## 🌿 Gitflow & Version Control
- **Proper Git Usage:** Strict adherence to clean version control practices.
- **Pull Requests:** Meaningful PRs with proper documentation, reviews, and descriptions.
- **Descriptive Commits:** Clear, semantic commit messages following best practices (e.g., Conventional Commits).
- **Branching Strategy:** Proper segregation of features, bug fixes, and hotfixes using dedicated branches.
- **Standardized Branches:** Use of consistent naming conventions for branches (e.g., `feature/`, `bugfix/`, `release/`).
- **Platform Management:** Efficient repository organization and team collaboration using **GitHub**.


## 📖 Project Documentation

Detailed project specifications, architectural decisions, and technical guides can be found in the Wiki:

* **[Getting Started](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/Getting-Started):** Step-by-step instructions on environment setup, installation, and running the project locally.
* **[Agile Management & User Stories](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/Project-Management-&-User-Stories):** Documentation of our Scrum workflow, 2-sprint execution, team roles, Jira tracking, and user stories.
* **[Analysis and Diagrams](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/Analysis-and-diagrams):** Use case specifications, flowcharts, and system requirement details.
* **[Architecture](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/Architecture):** Overview of the decoupled structure, database schemas, and folder organization (controllers, services, and repositories).
* **[Security](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/Security):** Implementation details for data validation, password hashing, and token-based authentication (JWT).
* **[API Reference](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/API-Reference):** Complete documentation of RESTful routes, request payloads, and structured HTTP responses.
* **[Testing](https://github.com/FactoriaF5-Asturias/project-p5-digital-academy-team1-restaurant-backend/wiki/Testing):** Guidelines for running unit tests, integration tests, and validating endpoint behaviors.

### Test Coverage

Generate the JaCoCo coverage report and verify that the project meets the minimum coverage requirement (70%) by running this command from the repository root:

```bash
./mvnw clean verify
```

On Windows, use the Maven wrapper script:

```powershell
.\mvnw.cmd clean verify
```

The build fails if total instruction coverage is below 70%. After a successful build, open `target/site/jacoco/index.html` in a browser to see coverage totals and details by package and class.



<h2 align="center">👨‍💻 Backend Development Team</h2>

<table align="center">
  <tr>
    <th>Developer</th>
    <th>GitHub profile</th>
  </tr>
  <tr>
    <td>Daniel Muntyanu</td>
    <td><a href="https://github.com/danielmuntyanu"><img src="docs/assets/github-logo-readme.png" alt="GitHub profile" width="20" height="20"></a> <a href="https://github.com/danielmuntyanu">@danielmuntyanu</a></td>
  </tr>
  <tr>
    <td>Alexa Pop</td>
    <td><a href="https://github.com/Alexapop"><img src="docs/assets/github-logo-readme.png" alt="GitHub profile" width="20" height="20"></a> <a href="https://github.com/Alexapop">@Alexapop</a></td>
  </tr>
  <tr>
    <td>Jose Cgh</td>
    <td><a href="https://github.com/Josecgh"><img src="docs/assets/github-logo-readme.png" alt="GitHub profile" width="20" height="20"></a> <a href="https://github.com/Josecgh">@Josecgh</a></td>
  </tr>
  <tr>
    <td>Rana Afkari</td>
    <td><a href="https://github.com/Raana-1375"><img src="docs/assets/github-logo-readme.png" alt="GitHub profile" width="20" height="20"></a> <a href="https://github.com/Raana-1375">@Raana-1375</a></td>
  </tr>
</table>
