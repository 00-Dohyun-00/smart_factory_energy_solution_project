# Nginx 없이 Vite 개발 서버를 그대로 띄웁니다 (지금 단계는 배포용 빌드 없이 단순하게)
FROM node:22-slim
WORKDIR /app
COPY package.json package-lock.json .
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
