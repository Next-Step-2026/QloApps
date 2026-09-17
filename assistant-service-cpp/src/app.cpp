#include "app.hpp"
#include <csignal>
#include <iostream>

namespace assistant {

Application* Application::instance_ = nullptr;

Application::Application(const std::string& host, int port)
    : host_(host), port_(port), server_(std::make_unique<http::HttpServer>(host, port)) {
    instance_ = this;
}

Application::~Application() {
    if (instance_ == this) {
        instance_ = nullptr;
    }
}

void Application::setupSignalHandlers() {
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);
}

void Application::signalHandler(int signum) {
    std::cout << "\n[QLO-FEAT-001] Received signal " << signum << ", shutting down gracefully..." << std::endl;
    if (instance_) {
        instance_->stop();
    }
}

int Application::run() {
    setupSignalHandlers();
    server_->start();
    return 0;
}

void Application::stop() {
    if (server_) {
        server_->stop();
    }
}

} // namespace assistant
