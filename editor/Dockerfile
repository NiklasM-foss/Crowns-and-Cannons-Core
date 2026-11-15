# Rechte-Editor fuer Crowns Core: der LuckPerms-Web-Editor (LuckPermsWeb, MIT-Lizenz,
# https://github.com/LuckPerms/LuckPermsWeb) mit Crowns-Core-Logo, ohne Statistik-Skript
# und ohne Verbindung zu luckperms.net.

FROM node:22-alpine AS build
ARG LPW_COMMIT=96c79d7f6d6524ada6e46e27ff48d1dbba9b8900
RUN apk add --no-cache git
WORKDIR /app
RUN git clone https://github.com/LuckPerms/LuckPermsWeb.git . \
    && git checkout "$LPW_COMMIT" \
    && git submodule update --init
RUN npm install
COPY config.json ./config.json
COPY branding/ /branding/
COPY branding.js /branding/branding.js
COPY demo/editor-demo.json /branding/editor-demo.json
COPY meta/ /branding/meta/
COPY branding.sh /branding.sh
RUN sh /branding.sh && npm run build

FROM nginx:alpine
COPY site-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/LICENSE.txt /usr/share/nginx/html/LICENSE-LuckPermsWeb.txt
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
