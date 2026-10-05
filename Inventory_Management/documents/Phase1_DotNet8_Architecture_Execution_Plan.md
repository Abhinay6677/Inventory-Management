# Phase 1 .NET 8 Execution Plan - Inventory Management System

## Current Repository Reality
- Current backend stack is Java Spring Boot.
- Current frontend is React (non-MUI baseline).
- No .NET SDK is installed on this machine.
- SonarQube endpoint localhost:9001 is not reachable at this time.

## Mandatory Prerequisites
1. Install .NET SDK 8.
2. Install MySQL 8 and ensure instance access.
3. Start SonarQube at localhost:9001.
4. Install Node.js LTS for frontend workflows.

## Target Clean Architecture
- backend/InventoryManagementSystem.Api
- backend/InventoryManagementSystem.Application
- backend/InventoryManagementSystem.Domain
- backend/InventoryManagementSystem.Infrastructure
- backend/InventoryManagementSystem.UnitTests
- backend/InventoryManagementSystem.IntegrationTests
- frontend (React + TypeScript + Material UI)

## Domain Model (Phase 1)
- User
- Role
- UserRole
- Product
- Category
- InventoryRequest
- AuditLog
- RefreshToken

## API Modules (Phase 1)
- Authentication: register, login, refresh, revoke.
- User Management: profile, user administration.
- Product Management: CRUD + search + paging + filters.
- Category Management: CRUD.
- Inventory Requests: create, list by user, manager queue, approve, reject.
- Dashboard: counts and analytics summary.
- Audit Logs: secured read endpoints.

## Security Baseline
- JWT bearer authentication.
- Refresh tokens persisted with rotation and revocation.
- Role authorization for InventoryUser, InventoryManager, Admin.
- FluentValidation on all requests.
- Global exception middleware.
- Secure headers middleware.
- Rate limiting middleware.
- Serilog structured logging.

## SonarQube Target (localhost:9001)
- Reliability A
- Security A
- Maintainability A
- Coverage > 90%
- Passed quality gate

## Sonar Scanner Flow (.NET)
1. dotnet sonarscanner begin /k:"InventoryManagementSystem" /d:sonar.host.url="http://localhost:9001" /d:sonar.token="YOUR_TOKEN" /d:sonar.cs.opencover.reportsPaths="**/coverage.opencover.xml"
2. dotnet build
3. dotnet test /p:CollectCoverage=true /p:CoverletOutputFormat=opencover
4. dotnet sonarscanner end /d:sonar.token="YOUR_TOKEN"

## Immediate Next Action
Run phase1-dotnet-bootstrap.ps1 after installing .NET SDK 8 to generate the complete solution skeleton and package wiring.
