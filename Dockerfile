FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY ./publish .
RUN mkdir -p /tmp/keys && chown -R $APP_UID:0 /tmp/keys
USER $APP_UID
EXPOSE 7860
ENV ASPNETCORE_HTTP_PORTS=7860
ENV DataProtection__KeysPath=/tmp/keys
ENTRYPOINT ["dotnet", "BeNobat.Web.dll"]

