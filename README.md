# PR Cluster Checker (Java & Angular Edition)

## Purpose

`pr-cluster-checker` is a web application designed to help development and release teams verify that pull requests (PRs) marked for specific deployment clusters have been correctly cherry-picked from a main release branch into each required cluster-specific release branch.

This version is a reimplementation of the original concept using a Java Spring Boot backend and an Angular frontend.

## Architecture Overview

The application consists of two main parts:

-   **Backend (Java - Spring Boot)**: Provides a REST API for configuration management, GitHub interaction, and PR analysis logic.
-   **Frontend (Angular)**: A single-page application (SPA) that consumes the backend API to provide a user interface for configuration, viewing dashboard results, and checking individual PRs.

## Backend (Java - Spring Boot)

### Purpose/Features

-   Manages application configuration (Git repository, branches, clusters).
-   Interacts with the GitHub API to fetch PR data, commit history, and compare branches.
-   Performs analysis to identify PRs missing from target cluster branches.
-   Exposes REST endpoints for the frontend.
-   Includes in-memory caching for GitHub API responses to improve performance.

### Prerequisites

-   Java JDK 17 or newer
-   Apache Maven 3.6+

### Setup Instructions

1.  **Clone the repository (if applicable).**
    The backend is located in the root `pr-cluster-checker` directory (or the main project directory).

2.  **Environment Variables for GitHub Authentication:**
    The application uses environment variables for GitHub authentication. Set these in your operating system or IDE environment:
    *   **For Personal Access Token (PAT) authentication (Recommended):**
        ```
        GITHUB_TOKEN=your_github_personal_access_token_here
        ```
        Create a PAT from your GitHub account settings with `repo` scope.
    *   **For Username/Password authentication (Less Recommended):**
        ```
        GITHUB_USERNAME=your_github_username
        GITHUB_PASSWORD=your_github_password_or_pat
        ```
        *Note: If 2FA is enabled, you must use a PAT as the password.*

3.  **Building the Backend:**
    Navigate to the project root directory (`pr-cluster-checker`) and run:
    ```bash
    mvn clean install
    ```
    This will compile the code, run tests, and package the application into a JAR file (e.g., `target/pr-cluster-checker-0.0.1-SNAPSHOT.jar`).

4.  **Running the Backend:**
    You can run the application using:
    *   `java -jar target/pr-cluster-checker-0.0.1-SNAPSHOT.jar`
    *   Or, directly via Maven: `mvn spring-boot:run`

5.  **API Port:**
    The backend API will typically be available on port `8080`. Example: `http://localhost:8080`.

### API Endpoints

The backend provides the following main REST API endpoints under the `/api` base path:

-   `GET /api/config`: Retrieves the current application configuration.
-   `POST /api/config`: Saves the application configuration.
-   `GET /api/dashboard`: Fetches analysis results (missing PRs by cluster). Accepts query parameters for date filtering (`dateFrom`, `dateTo`) and cluster name (`filterClusterName`).
-   `GET /api/check_pr`: Retrieves detailed cherry-pick status for a specific PR number. Requires `prNumber` query parameter.
-   `POST /api/clear_cache`: Clears the backend's in-memory cache for GitHub API data.

## Frontend (Angular)

### Purpose/Features

-   Provides a user-friendly web interface to configure the application.
-   Displays a dashboard of PRs missing from their target clusters.
-   Allows users to check the cherry-pick status of individual PRs.
-   Communicates with the Java backend via REST API calls.

### Prerequisites

-   Node.js (v18.19.0 or v20.11.0+ recommended, as Angular 17+ is used)
-   Angular CLI (Version 17 was used for development, installed via `npx`)

### Setup Instructions

1.  **Navigate to the Frontend Directory:**
    ```bash
    cd pr-cluster-checker-angular
    ```

2.  **Install Dependencies:**
    If `node_modules` directory is not present or to ensure all dependencies are up-to-date:
    ```bash
    npm install
    ```

3.  **Running the Frontend Development Server:**
    ```bash
    npx ng serve
    ```
    This will start a development server, typically on `http://localhost:4200/`.

4.  **Accessing the Application:**
    Open `http://localhost:4200` in your web browser. The application should load, and you can navigate between pages.

**Important:** The Java backend API must be running (typically on `http://localhost:8080`) for the Angular frontend to fetch and display data correctly.

## Core Functionality/Usage

The application flow is similar to the original Python version, but through the new Angular UI:

1.  **Configuration Page (`/config`):**
    -   Set the Git Repository URL, Main Release Branch.
    -   Choose GitHub authentication method (PAT or Username/Password). Credentials are read from backend environment variables.
    -   Define target clusters with their names and corresponding release branch names.
    -   Optionally set a default date range for PR analysis.
    -   Save the configuration.

2.  **Dashboard Page (`/dashboard`):**
    -   Displays PRs merged into the main release branch but found to be missing from one or more of their target cluster release branches.
    -   Allows filtering by date range and specific cluster name.

3.  **PR Checker Page (`/check-pr`):**
    -   Input a PR number to get its detailed status:
        -   Merge status to the main branch.
        -   Merge commit SHA.
        -   Clusters it was marked for (from PR body).
        -   Cherry-pick status for all configured clusters.

## PR Body Template Expectation

For the tool to correctly identify which clusters a PR impacts, the PR body (description) **must** contain a section like the following:

```markdown
```cluster
cluster-a
cluster-b
```
Alternatively, a single-line format is also supported:
`cluster: cluster-a cluster-b`

Cluster names used here should match the cluster names defined in the tool's configuration page (case-insensitive matching is attempted for "cluster:" line, but exact match for ```cluster``` block is better).

## Caching

-   **Backend**: The Java backend uses an in-memory cache for GitHub API responses (PR lists, commit data, branch comparisons) to improve performance and reduce API rate limit consumption.
-   **Frontend**: A "Clear Backend Cache & Refresh" button is available on the Dashboard page to clear this server-side cache and reload data.

## Testing Environment & Mock Repository

For thorough end-to-end testing, setting up a mock GitHub repository is recommended, similar to the original Python version's advice. This allows safe testing of PR fetching, branch comparison, and cherry-pick detection logic. Configure the application to point to this mock repository.

## Security Note

-   Always prefer using GitHub Personal Access Tokens (PATs) over username/password for authentication.
-   Store your GitHub PAT or credentials as environment variables for the backend application.
-   The configuration saved by the application (`data/config.json` on the backend) does **not** store sensitive credentials like tokens or passwords. It only stores the chosen authentication method and, optionally, the username (if using username/password auth).
