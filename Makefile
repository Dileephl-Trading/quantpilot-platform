.PHONY: up down logs test frontend backend

up:
	docker compose --env-file .env up --build -d

down:
	docker compose --env-file .env down

logs:
	docker compose --env-file .env logs -f

test:
	cd backend && mvn test

backend:
	cd backend && mvn spring-boot:run

frontend:
	cd frontend && npm ci && npm run dev
