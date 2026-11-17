FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY ["src/BeNobat.Web/BeNobat.Web.csproj", "src/BeNobat.Web/"]
COPY ["Directory.Build.props", "./"]
COPY ["NuGet.Config", "./"]
RUN dotnet restore "src/BeNobat.Web/BeNobat.Web.csproj"
COPY . .
WORKDIR "/src/src/BeNobat.Web"
RUN dotnet publish "BeNobat.Web.csproj" -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY --from=build /app/publish .
RUN mkdir -p /keys && chown -R $APP_UID:0 /keys
USER $APP_UID
EXPOSE 8080
ENV ASPNETCORE_HTTP_PORTS=8080
ENTRYPOINT ["dotnet", "BeNobat.Web.dll"]

