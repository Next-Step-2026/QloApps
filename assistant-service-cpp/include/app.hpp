#pragma once

#include <string>
#include <memory>
#include "http_server.hpp"

namespace assistant {

class Application {
public:
    Application(const std::string& host = "127.0.0.1", int port = 8101);
    ~Application();

    int run();
    void stop();

private:
    static void setupSignalHandlers();
    static void signalHandler(int signum);

    std::string host_;
    int port_;
    std::unique_ptr<http::HttpServer> server_;
    static Application* instance_;
};

} // namespace assistant
