import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link, NavLink } from 'react-router-dom';
import Schedule from './components/Schedule';
import Teachers from './components/Teachers';
import Courses from './components/Courses';
import Rooms from './components/Rooms';
import Groups from './components/Groups';
import Assignments from './components/Assignments';

function App() {
  return (
    <Router>
      <div className="app">
        <header className="header">
          <div className="container">
            <h1>Schedule Maker</h1>
            <nav className="nav">
              <NavLink to="/" className={({ isActive }) => isActive ? 'active' : ''}>
                Schedule
              </NavLink>
              <NavLink to="/teachers" className={({ isActive }) => isActive ? 'active' : ''}>
                Teachers
              </NavLink>
              <NavLink to="/courses" className={({ isActive }) => isActive ? 'active' : ''}>
                Courses
              </NavLink>
              <NavLink to="/rooms" className={({ isActive }) => isActive ? 'active' : ''}>
                Rooms
              </NavLink>
              <NavLink to="/groups" className={({ isActive }) => isActive ? 'active' : ''}>
                Groups
              </NavLink>
              <NavLink to="/assignments" className={({ isActive }) => isActive ? 'active' : ''}>
                Assignments
              </NavLink>
            </nav>
          </div>
        </header>
        
        <div className="container">
          <Routes>
            <Route path="/" element={<Schedule />} />
            <Route path="/teachers" element={<Teachers />} />
            <Route path="/courses" element={<Courses />} />
            <Route path="/rooms" element={<Rooms />} />
            <Route path="/groups" element={<Groups />} />
            <Route path="/assignments" element={<Assignments />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}

export default App;

