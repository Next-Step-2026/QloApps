# AGENTS.md

## Project Overview

QloApps is an open-source hotel reservation and property management platform. It enables hotels to manage rooms, bookings, guests, and payments through a web-based system.

## Purpose of this File

This document provides guidance for AI coding agents contributing to the QloApps project. It defines conventions, safety rules, and workflows to ensure consistent and secure code generation.

## Technology Stack

- **Language:** PHP 8.1–8.4 (backend), Smarty 3.x (templates), JavaScript/jQuery (frontend)
- **Database:** MySQL 5.7, 8.0+; MariaDB 10.5, 10.6, 10.11, 11.0, 11.2, 11.4
- **Architecture:** MVC with hook-based module system
- **License:** OSL-3.0 (core), AFL-3.0 (modules)
- **Required PHP Extensions:** PDO_MySQL, cURL, OpenSSL, SOAP, GD, SimpleXML, DOM, Zip, Phar
- **Recommended Extensions (Docker / Production):** Intl, Opcache

## Environment Setup

### 1. Docker Compose Setup (Recommended)

The project includes a containerized development stack with PHP 8.2 (Apache) and MariaDB 10.11:

```bash
# Build and start services
docker compose up -d

# Stop services
docker compose down
```

**Service Details & Credentials:**
- **Web Server (`qlo-app`):** Port `8080` (mapped to container port `80`)
- **Database Server (`qlo-db`):** Port `3306`
  - Host: `qlo-db` (from containers) or `127.0.0.1:3306` (from host)
  - Database: `qloapps`
  - User: `qloapps`
  - Password: `qlopassword`
  - Table Prefix: `qlo_`

### 2. File & Directory Permissions

The web server in Docker runs as user `www-data` (UID 33). The following directories require recursive read/write permissions for the application and installer to function properly:

```bash
chmod 755 . ..
chmod -R a+rwX cache config download img log mails modules themes translations upload
```

### 3. Remote Development & Port Forwarding

When developing on a remote host (e.g., Cloudtop / remote SSH workstation):
- **VS Code Web Server (Zero-Lag Web IDE for ChromeOS / Remote):** Run the built-in VS Code Web Server on the remote host:
  ```bash
  code serve-web --host 0.0.0.0 --port 8000 --without-connection-token --accept-server-license-terms --default-folder /usr/local/google/home/joaolisboa/projeto/QloApps
  ```
  Access directly via `http://<host>:8000/` or forward port 8000 in SSH (`-L 8000:localhost:8000` -> `http://localhost:8000`).
- **Chrome Remote Desktop (Cloudtop GUI):** Connect via `remotedesktop.google.com/access` and open `http://localhost:8080` directly in the workstation's Chrome.
- **Chrome Secure Shell (nassh on ChromeOS):** In connection profile settings, set **SSH Arguments** to `-L 8080:127.0.0.1:8080 -L 8000:127.0.0.1:8000`.
- **VS Code / Cursor Remote - SSH:** In the **Ports** panel, click **Forward a Port** and enter `8080`.
- **SSH CLI:** `ssh -L 8080:127.0.0.1:8080 -L 8000:127.0.0.1:8000 -L 3306:127.0.0.1:3306 <user>@<host>`

### 4. Canonical Domain, Proxies & Redirect Loops

PrestaShop/QloApps enforces canonical URL checking and cookie IP matching. When accessing via a port-forwarded URL or corporate web proxy (e.g., `*.proxy.googlers.com`), update database records to prevent 301/302 redirect loops and silent session logouts caused by rotating proxy IPs:

```sql
-- Disable canonical redirects and cookie IP checking (critical behind corporate proxies)
UPDATE qlo_configuration SET value = '0' WHERE name IN ('PS_CANONICAL_REDIRECT', 'PS_COOKIE_CHECKIP');

-- Set shop domain (use localhost:8080 or the proxy domain)
UPDATE qlo_shop_url SET domain = 'localhost:8080', domain_ssl = 'localhost:8080', physical_uri = '/' WHERE id_shop_url = 1;
UPDATE qlo_configuration SET value = 'localhost:8080' WHERE name IN ('PS_SHOP_DOMAIN', 'PS_SHOP_DOMAIN_SSL');
```

### 5. Dependency Installation & Cache Management

Install PHP dependencies:
```bash
composer install
```

Clear caches (run whenever modifying templates, overrides, or domain settings):
```bash
rm -rf cache/smarty/compile/* cache/smarty/cache/*
rm -f cache/class_index.php
```

Clear class cache after adding or modifying overrides.

## Project Structure

```
.
├── classes/              # Core models extending ObjectModel
├── controllers/          # Front and admin controllers
│   ├── admin/
│   └── front/
├── modules/              # Feature modules with isolated functionality
├── override/             # Core class and controller overrides
├── themes/               # Smarty templates (.tpl files)
├── config/               # Configuration files (settings.inc.php contains secrets)
├── cache/                # Generated cache files
├── tests/                # PHPUnit test suite
```

**QloApps Agent Skills:** Install reusable development skills using:
```bash
npx skills add Qloapps/agent-skills
```
Available skills: module-development, payment-module-development, stats-module-development. Check installed skills before implementing new functionality.

## Architecture Overview

**MVC Pattern**
- Models: classes/ extending ObjectModel
- Controllers: FrontController or AdminController
- Views: Smarty templates (.tpl)

**Modules**
- Located in modules/<modulename>/
- Provide isolated functionality
- Integrate using hooks

**Overrides**
- Located in override/
- Extend core classes using the Core suffix
- Clear class cache after creating overrides

**Context**
- Access runtime objects using Context::getContext()

## Core vs Module Development Rules

**When working on a module:**
1. Never modify core files directly
2. Use **hooks** to integrate module functionality into core features
3. If no suitable hook exists, create a **custom hook** — but only if the hook placement is generic and useful for other modules too
4. Use **overrides** only as a last resort — overrides can conflict with other module override files and require manual resolution

**When working on a core feature:**
- Make changes directly in core files
- Do not use hooks or overrides for core-to-core changes

## Coding Conventions

**Classes:** PascalCase — `HotelBookingData`
**Methods:** camelCase — `getBookingDetails()`
**Variables:** camelCase — `$hotelId`
**Constants:** UPPER_SNAKE_CASE — `BOOKING_STATUS_CONFIRMED`
**Database Tables:** _DB_PREFIX_ + lowercase_snake — `qlo_hotel_booking`
**Config Keys:** MODULENAME_SETTING — `HOTELRESERVATION_ENABLED`
**Files:** One class per file, filename matches class name
**Templates:** lowercase-hyphens.tpl — `booking-form.tpl`

Add PHPDoc blocks to all classes and methods.

## Translation

Never hardcode user-facing English strings — always wrap them in the appropriate translation method.

| Context | Method |
|---------|--------|
| Module main file | `$this->l('string')` |
| Module admin controller | `$this->l('string')` |
| Module front controller | `$this->module->l('string', 'controllerName')` |
| Module classes | `$objModule->l('string', 'ClassName')` |
| Core admin controller | `$this->l('string')` |
| Core front controller | `Tools::displayError('string')` |
| Smarty template (core) | `{l s='string'}` |
| Smarty template (module) | `{l s='string' mod='modulename'}` |

## Multi-language

- Always include `id_lang` in queries that return translatable content
- Use `Context::getContext()->language->id` for the current language

## Module Development Guidelines

**Module Location:** modules/<modulename>/

**Required Files:**
- <modulename>.php — Main class extending Module
- config.xml — Module metadata

**Optional Directories:**
- classes/ — Module-specific models
- controllers/ — Module controllers
- views/templates/ — Smarty templates
- upgrade/ — Version migration scripts

**Hook Integration:**
- Register hooks in install() method
- Unregister hooks in uninstall() method
- Keep hook handlers lightweight

**Configuration:**
- Store settings using Configuration::updateValue()
- Retrieve settings using Configuration::get()
- Prefix config keys with module name

## Database Rules

**Table Prefix:** Always use _DB_PREFIX_ constant instead of hardcoding the table prefix

**Escaping:**
- Strings: pSQL($value)
- Integers: (int)$value
- Table/column names: bqSQL($name)

**Preferred Access:** Use ObjectModel for CRUD operations instead of raw SQL queries

**Prohibited:** Never concatenate raw user input into SQL queries.

## Security Guidelines

**Input Handling**
- Use Tools::getValue() for request parameters
- Cast numeric values to (int)
- Escape strings using pSQL()
- Validate input using Validate class methods

**Output Escaping**
- Use Tools::safeOutput() in PHP
- Use Smarty escape modifiers in templates

**Sensitive Data**
- Never expose config/settings.inc.php
- Never commit API keys, passwords, or tokens

**Authorization**
- Verify permissions before performing admin operations

## Testing

Testing infrastructure is being configured. Check tests/ directory for available tests before running.

## AI Agent Workflow

1. Check installed agent skills before implementing new functionality
2. Search codebase for similar implementations before creating new code
3. Extend existing classes rather than duplicating functionality
4. Follow patterns established in surrounding code
5. Reuse existing utilities: Tools, Validate, Db, Configuration classes
6. Prioritize consistency with existing codebase over new approaches

After making changes:
- Clear caches if modifying templates or overrides
- Add PHPDoc to new methods

## Git Workflow & Team Conventions

### 1. Branch Naming (GitHub Flow)
- **Format:** `feature/RFC-00X-descricao-da-tarefa` or `fix/RFC-00X-descricao-do-bug` (kebab-case)
- **Active Task Branch:** `feature/RFC-008-busca-entidades-acionaveis`
- Create short-lived sub-branches for specific components and merge into the main task branch.

### 2. Conventional Commits (Strictly in English)
All commit messages must follow the Conventional Commits specification with descriptions in English using the imperative mood:
`<type>[optional scope]: <description>`

Tags:
- `feat`: New feature or capability
- `fix`: Bug fix
- `docs`: Documentation updates only
- `style`: Formatting, missing semi-colons, white-space changes (no code logic changes)
- `refactor`: Refactoring code without changing behavior
- `perf`: Performance improvement
- `test`: Adding or correcting tests
- `chore`: Build tasks, package configs, dependencies, auxiliary tools
- `build`: Build system or external dependencies
- `ci`: Continuous Integration / CD configuration files and scripts

### 3. GitHub HTTPS Authentication
When pushing to GitHub over HTTPS, personal passwords are not supported. Use a Personal Access Token (PAT):
- **Fine-grained PAT:** Must have **Contents: Read and write** permission enabled under *Repository permissions*.
- **Classic PAT:** Must have the **`repo`** scope enabled.

### 4. Pull Request Standards
Each PR must include:
- **Description & Context:** Problem solved and why it was needed.
- **Summary of Changes:** Bulleted list of code modifications.
- **Testing Instructions:** Step-by-step reproduction and validation guide.
- **Checklist:** Project standards followed, no sensitive data exposed, tests updated.

## Repository & Development Hygiene

To keep the development repository clean and prevent committing local installation or generated assets:
- **Do not commit `config/settings.inc.php`:** Contains local environment secrets, database credentials, and cookie encryption keys.
- **Do not commit generated admin folders:** PrestaShop/QloApps renames `admin/` to a random directory (e.g., `admin847azx/`) post-installation. Add any custom generated `admin*/` to `.git/info/exclude` rather than modifying `.gitignore`.
- **Do not commit generated images or uploads:** Keep local test images in `img/`, uploads in `upload/`, and temporary cache files untracked.
- **Post-installation cleanup:** Do not delete or rename the `install/` folder. Instead, use the marker file `touch install/.installed`. QloApps will recognize this marker, automatically deactivate the installer endpoint (redirecting to `/`), and allow Back Office login without marking the 2,840 installer files as deleted in Git.

## Safety Rules

**Agents must not:**
- Delete files unless explicitly instructed
- Run git commands automatically
- Modify composer.json without approval
- Modify config/settings.inc.php
- Execute DROP, TRUNCATE, or destructive SQL
- Modify core files directly when working on a module (use hooks or override system)

**Agents must:**
- Use pSQL() for strings and (int) for IDs in SQL
- Use Tools::getValue() for request parameters
- Escape all output
- Check installed agent skills before implementing new functionality
- Follow project naming conventions
- Validate inputs before processing

**Agents should ask before:**
- Deleting any files
- Running git commands
- Changing dependencies
- Modifying database schema
- Altering payment or booking logic

---

**Resources:**
- Documentation: https://docs.qloapps.com
- Forum: https://forums.qloapps.com
- GitHub: https://github.com/Qloapps/QloApps
- Security Issues: support@qloapps.com
