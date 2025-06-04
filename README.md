# PR Cluster Checker

## Purpose

`pr-cluster-checker` is a Python Flask web application designed to help development and release teams verify that pull requests (PRs) marked for specific deployment clusters have been correctly cherry-picked from a main release branch into each required cluster-specific release branch.

It aims to prevent accidental omissions of critical PRs from cluster deployments.

## Features

- **Configuration Page (`/config`)**:
    - Input Git repository URL.
    - Specify authentication method (Personal Access Token or Username/Password). Credentials are read from a `.env` file.
    - Define the main release branch (e.g., `ENH-83-RC-1`).
    - List cluster names (e.g., `smi`, `smc`) and their corresponding cluster-specific release branch names (e.g., `ENH.83.RC.1.smi-branch`).
    - Set a date range to filter PRs merged into the main release branch.
- **Dashboard Page (`/dashboard`)**:
    - Displays PRs that were merged into the main release branch (within the configured date range) and marked for specific clusters (via a special PR body template) but have NOT been cherry-picked into one or more of their target cluster release branches.
    - Lists missing PRs under each relevant cluster release branch.
    - Provides filters for:
        - Date range (overrides the global config date range for the current view).
        - Specific cluster name.
- **PR Checker Page (`/check_pr`)**:
    - Allows a user to input a specific PR number.
    - Shows:
        - Whether the PR is merged into the main release branch.
        - Its merge commit SHA on the main branch (if merged).
        - Which clusters were marked for impact in its body.
        - The cherry-pick status for each configured cluster release branch.
- **Caching**:
    - Caches GitHub API responses for PR lists and cherry-pick statuses to improve performance and reduce API rate limit consumption. Cache files are stored in the `data/` directory.

## PR Body Template Expectation

For the tool to correctly identify which clusters a PR impacts, the PR body (description) **must** contain a section like the following:

```markdown
### Cluster Impact
Please select impacted clusters:
- [x] SMI
- [ ] SMC
- [x] SMN4
```
The tool parses this section to determine the intended target clusters for a PR. Cluster names used here should match the cluster names defined in the tool's configuration page.

## Setup Instructions

### Prerequisites
- Python 3.8+
- Git

### Installation
1.  **Clone the repository (if applicable) or ensure project files are present.**
    ```bash
    # git clone <repository_url>
    # cd pr-cluster-checker
    ```

2.  **Create a Python virtual environment:**
    (The command involves using Python's `venv` module)
    ```bash
    # Example:
    # python -m venv [name_for_your_environment_directory]
    # e.g., python -m venv .myenv
    ```

3.  **Activate the virtual environment:**
    -   On macOS and Linux (replace `.myenv` if you chose a different name):
        ```bash
        source .myenv/bin/activate
        ```
    -   On Windows:
        ```bash
        .myenv\Scripts\activate
        ```

4.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

5.  **Create a `.env` file for GitHub credentials:**
    In the root directory of the project, create a file named `.env`. This file will store your GitHub credentials. **This file is ignored by Git via `.gitignore` and should NOT be committed.**

    Choose **one** of the following authentication methods:

    -   **Using a Personal Access Token (PAT):**
        Create a PAT from your GitHub account settings with the necessary scopes (e.g., `repo` for accessing repository data, including private ones).
        Add the following line to your `.env` file:
        ```
        GITHUB_TOKEN=your_github_personal_access_token_here
        ```

    -   **Using Username/Password (Less Recommended):**
        If you choose to use your GitHub username and password (not recommended for programmatic access, especially if you have 2FA enabled, as PATs are more secure and manageable), add these lines to your `.env` file:
        ```
        GITHUB_USERNAME=your_github_username
        GITHUB_PASSWORD=your_github_password_or_pat_if_username_is_set
        ```
        *Note: If you have 2FA enabled, your password will likely not work directly. You might need to use a PAT as the password in this case as well, or GitHub might block the login.*

## Running the Application

1.  **Ensure your virtual environment is activated.**
2.  **Run the Flask application:**
    ```bash
    python run.py
    ```
    This will typically start the development server on `http://127.0.0.1:5001` (or another port if `run.py` is configured differently).

3.  **Access the application in your web browser:**
    Open `http://127.0.0.1:5001` (or the relevant address shown in your terminal). You will be redirected to the Dashboard, or to the Configuration page if the app hasn't been configured yet.

## Usage

1.  **Configuration (`/config`):**
    -   Navigate to the `/config` page first.
    -   **Git Repository URL**: Enter the full HTTPS or SSH URL of the GitHub repository you want to monitor (e.g., `https://github.com/owner/repository.git`).
    -   **Authentication Method**: Select "Personal Access Token" or "Username/Password". Ensure your `.env` file is set up accordingly. If using "Username/Password", you can enter your username in the form for reference (it's saved in `config.json`), but the password always comes from `.env`.
    -   **Main Release Branch Name**: The primary branch from which PRs are merged and subsequently cherry-picked (e.g., `main`, `develop`, `release/1.2.x`).
    -   **Cluster Branches**:
        -   Click "Add Cluster" to add entries.
        -   For each entry:
            -   **Cluster Name**: A short, unique identifier for the cluster (e.g., `smi`, `prod-us-east`, `dev-gcp`). This name **must match** the names used in the PR body's "Cluster Impact" section.
            -   **Corresponding Release Branch**: The full name of the Git branch associated with this cluster where cherry-picks are expected (e.g., `releases/prod-us-east-v1.2`, `hotfix/smi-integration`).
    -   **Date Range for PRs**:
        -   **From Date / To Date**: Define the window for fetching PRs that were merged into the main release branch. Only PRs merged within this range will be analyzed. Leave blank if no specific date filtering from the main configuration is desired (though the Dashboard also offers its own date filters).
    -   Click "Save Configuration".

2.  **Dashboard (`/dashboard`):**
    -   This page shows PRs that are considered "missing". A PR is missing if:
        -   It was merged into the main release branch (within the active date filter).
        -   Its body was marked for a specific cluster.
        -   It has not been found in that cluster's corresponding release branch.
    -   Use the filters at the top to narrow down by date range or a specific cluster name.

3.  **PR Checker (`/check_pr`):**
    -   Enter a PR number from your repository and click "Check PR".
    -   The page will display:
        -   The PR's title and link.
        -   Whether it was found as merged into your configured main release branch.
        -   Its merge commit SHA on the main branch (if applicable).
        -   The list of clusters it was marked for in its body.
        -   A table showing each cluster you've configured, its release branch, and whether the PR's main branch merge commit is present in that cluster branch's history.

## Testing Environment & Mock Repository

For thorough end-to-end testing, especially of the GitHub interaction logic, it's highly recommended to set up a mock/sample GitHub repository with the following structure:

-   A main release branch (e.g., `main`).
-   Several cluster-specific release branches (e.g., `release/smi`, `release/smc`).
-   A `.github/pull_request_template.md` file in the mock repository containing the "Cluster Impact" section format described above.
-   Several sample PRs:
    -   Some merged into the main branch.
    -   Some of these merged PRs should be marked for certain clusters.
    -   Some of these marked PRs should then be cherry-picked (or merged) into their respective cluster branches.
    -   Some marked PRs should be deliberately *not* cherry-picked to some of their target cluster branches to test the "missing" detection.
    -   Some PRs can be left open or merged to other feature branches not relevant to the tool's main flow.

Configure the `pr-cluster-checker` tool to point to this mock repository to safely test its functionality.

## Security Note
- Always store your GitHub Personal Access Token or credentials in the `.env` file.
- Do **not** commit the `.env` file to your Git repository. The provided `.gitignore` should already prevent this.
- The configuration file (`data/config.json`) does **not** store sensitive credentials like tokens or passwords. It only stores the chosen authentication method and, optionally, the username for reference if using username/password auth.
