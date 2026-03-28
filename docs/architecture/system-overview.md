OrderSphere is a cloud-native, microservices-based order management platform built using Spring Boot.
The system is designed to support reliable order processing, inventory control, and transparent auditability across multiple trusted organizations.

High-Level Components
	API Gateway – entry point for all client requests
	Auth Service – authentication and authorization
	Order Service – order lifecycle and saga orchestration
	Inventory Service – stock reservation and release
	Payment Service – asynchronous payment processing
	Shipping Service – shipment creation and tracking
	Notification Service – customer notifications
	Ledger Service – immutable audit trail using Hyperledger Fabric
	Reporting Service – read-optimized views for customer queries

Architectural Principles
	Each service owns its own data
	Services communicate primarily via events
	Failures are handled using saga-based compensation
	Blockchain is used only for audit and trust, not transactions
	Infrastructure is containerized and Kubernetes-native
