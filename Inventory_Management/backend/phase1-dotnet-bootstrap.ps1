param(
    [string]$Root = "C:\sts-4.31.0.RELEASE\Inventory_Management\InventoryManagementSystem"
)

$ErrorActionPreference = "Stop"

Write-Host "Creating solution at $Root" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $Root | Out-Null
Set-Location $Root

dotnet new sln -n InventoryManagementSystem

# Backend projects
$backend = Join-Path $Root "backend"
New-Item -ItemType Directory -Force -Path $backend | Out-Null
Set-Location $backend

dotnet new webapi -n InventoryManagementSystem.Api --framework net8.0 --use-controllers

dotnet new classlib -n InventoryManagementSystem.Application --framework net8.0

dotnet new classlib -n InventoryManagementSystem.Domain --framework net8.0

dotnet new classlib -n InventoryManagementSystem.Infrastructure --framework net8.0

dotnet new xunit -n InventoryManagementSystem.UnitTests --framework net8.0

dotnet new xunit -n InventoryManagementSystem.IntegrationTests --framework net8.0

Set-Location $Root

dotnet sln add .\backend\InventoryManagementSystem.Api\InventoryManagementSystem.Api.csproj
dotnet sln add .\backend\InventoryManagementSystem.Application\InventoryManagementSystem.Application.csproj
dotnet sln add .\backend\InventoryManagementSystem.Domain\InventoryManagementSystem.Domain.csproj
dotnet sln add .\backend\InventoryManagementSystem.Infrastructure\InventoryManagementSystem.Infrastructure.csproj
dotnet sln add .\backend\InventoryManagementSystem.UnitTests\InventoryManagementSystem.UnitTests.csproj
dotnet sln add .\backend\InventoryManagementSystem.IntegrationTests\InventoryManagementSystem.IntegrationTests.csproj

# Project references
Set-Location .\backend\InventoryManagementSystem.Api
dotnet add reference ..\InventoryManagementSystem.Application\InventoryManagementSystem.Application.csproj
dotnet add reference ..\InventoryManagementSystem.Infrastructure\InventoryManagementSystem.Infrastructure.csproj

Set-Location ..\InventoryManagementSystem.Application
dotnet add reference ..\InventoryManagementSystem.Domain\InventoryManagementSystem.Domain.csproj

Set-Location ..\InventoryManagementSystem.Infrastructure
dotnet add reference ..\InventoryManagementSystem.Application\InventoryManagementSystem.Application.csproj
dotnet add reference ..\InventoryManagementSystem.Domain\InventoryManagementSystem.Domain.csproj

Set-Location ..\InventoryManagementSystem.UnitTests
dotnet add reference ..\InventoryManagementSystem.Application\InventoryManagementSystem.Application.csproj
dotnet add reference ..\InventoryManagementSystem.Domain\InventoryManagementSystem.Domain.csproj
dotnet add package Moq
dotnet add package FluentAssertions

Set-Location ..\InventoryManagementSystem.IntegrationTests
dotnet add reference ..\InventoryManagementSystem.Api\InventoryManagementSystem.Api.csproj
dotnet add package Microsoft.AspNetCore.Mvc.Testing

# Core backend packages
Set-Location ..\InventoryManagementSystem.Infrastructure
dotnet add package Pomelo.EntityFrameworkCore.MySql
dotnet add package Microsoft.EntityFrameworkCore.Design
dotnet add package Microsoft.EntityFrameworkCore.Tools
dotnet add package Serilog.AspNetCore
dotnet add package Serilog.Sinks.Console
dotnet add package Serilog.Sinks.File

Set-Location ..\InventoryManagementSystem.Application
dotnet add package AutoMapper
dotnet add package FluentValidation

Set-Location ..\InventoryManagementSystem.Api
dotnet add package Microsoft.AspNetCore.Authentication.JwtBearer
dotnet add package Microsoft.AspNetCore.Mvc.Versioning
dotnet add package Microsoft.AspNetCore.Mvc.Versioning.ApiExplorer
dotnet add package Swashbuckle.AspNetCore

# Frontend scaffold (Vite React + TS)
Set-Location $Root
npm create vite@latest frontend -- --template react-ts
Set-Location .\frontend
npm install
npm install axios @mui/material @mui/icons-material @emotion/react @emotion/styled react-router-dom recharts react-hot-toast
npm install -D vitest @vitest/coverage-v8 @testing-library/react @testing-library/jest-dom @types/node

Write-Host "Bootstrap completed." -ForegroundColor Green
Write-Host "Next: configure appsettings, DB connection, JWT secret, and run migrations." -ForegroundColor Green
