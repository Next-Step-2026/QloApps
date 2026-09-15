#pragma once

#include <string>
#include <memory>

namespace httplib {
class Server;
}

namespace assistant::http {

class HttpServer {
public:
    HttpServer(const std::string& host = "127.0.0.1", int port = 8101);
    ~HttpServer();

    void start();
    void stop();

private:
    void registerRoutes();

    std::string host_;
    int port_;
    std::unique_ptr<httplib::Server> server_;
};

} // namespace assistant::http
