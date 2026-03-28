Project Scope
-------------
	OrderSphere is a cloud-native order and inventory management platform designed for environments where trust, auditability, and clear service boundaries are critical.

Target Users:
	Customers placing and tracking orders
	Trusted organizations such as inventory providers, logistics partners, and auditors

Core Capabilities:
	Secure user authentication and authorization
	Order lifecycle management
	Inventory reservation and release
	Asynchronous payment processing
	Shipment creation and tracking
	Event-driven notifications
	Immutable audit trail for order and inventory events using Hyperledger Fabric

Supported User Flows:
	Order placement and confirmation
	Order and shipment tracking
	Read-only audit and dispute verification

Out of Scope:
	Product recommendations
	Dynamic pricing
	Seller marketplace
	Returns and refunds
	Reviews and ratings
	AI/ML-based personalization

Design Goals:
	Microservices-based architecture
	Event-driven communication
	Eventual consistency with Saga pattern
	Kubernetes-native deployment
	Blockchain-based auditability for multi-organization trust
